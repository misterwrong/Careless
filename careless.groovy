/*
 * Copyright (C) 2016-2018 Mister Wrong <5786774+misterwrong@users.noreply.github.com>
 *
 * Careless
 *
 * Careless is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * Careless is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Careless. If not, see <http://www.gnu.org/licenses/>.
 */
 
import groovy.io.FileType
import static groovy.io.FileType.*
import java.nio.file.Files
import java.nio.file.Paths
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import groovy.transform.Canonical

@Grapes([
    @Grab(group='ch.qos.logback', module='logback-classic', version='1.0.13') 
])

@Slf4j
class Process {

    def trans    = "philae"                         // The name of the host of Transmission
    def ttrss    = "http://localhost/tt-rss/api/";  // The full URL to Tiny Tiny RSS, ending with /api/
    def user     = ""                               // Your Tiny Tiny RSS user name
    def pass     = ""                               // Your Tiny Tiny RSS password
    def feed     = ""                               // The feed id (numeric)
    
    def tv       = ""                               // The directory where the articles are stored
    def allowed  = ['mkv', 'mp4', 'mpg', 'avi']     // The allowed file name extensions
    def nrs      = "[Ss]?[0-9]{1,2}[EeXx][0-9]{1,2}"// The regex that determines the season and
    
    def sid = null

    public String login () {
          def message = """{
                \"op\": \"login\",
                \"user\": \"$user\",
                \"password\": \"$pass\"
           }"""
        def text = doPost(message).getText()
        def json = new JsonSlurper().parseText(text)
        log.debug "login result: $json.status (0 is Ok)"
        return json.content.session_id
    }
    
    public List getArticles(String sid) {
        def message = """{
            \"sid\": \"$sid\",
            \"op\": \"getHeadlines\",
            \"feed_id\": \"$feed\",
            \"view_mode\": \"unread\"
           }"""
        def text = doPost(message).getText()
        def json = new JsonSlurper().parseText(text)
        def n = json.content.size()
        log.debug("got $n articles for feed $feed")
        return filter(sid, json.content)
    }
    
    private List filter(String sid, List content) {
        def articles = []
        def existing = []
        
        new File(tv).eachFileRecurse(FILES) { file ->
            if (allowed.contains(file.name.substring(file.name.lastIndexOf('.') + 1))) {
                existing << file.name.substring(0, file.name.length() - 4).replaceAll("\'", "").toLowerCase()
            }
        }
                
        content.each { it -> 
            def article = getArticle(it)
            signoff(sid, it.id as String)
            
            if (!articles.contains(article) && !existing.contains(article.title.replaceAll("\'", "").toLowerCase())) {
                articles << article
            }
            else {
                log.debug("Not transmitting already existing article $article.title")
            }            
        }
        
        log.debug ("Transmitting $articles.size articles")
        
        return articles
    }
        
    private Article getArticle(Map heading) {        
        def link = heading.link
        def article = link.find(nrs)
        def s = link.indexOf("&dn=") + 4
        def e = link.indexOf(article)        
        [
            id: heading.id,
            title: (heading.link.substring(s, e) + "- " + article).replaceAll("\\+", " "),
            link: heading.link
        ] as Article
    }
    
    public void transmit(String sid, List articles) {
        articles.each { it ->
            log.debug "sending link: ($it.id) $it.link"
            try {
                log.error (['transmission-remote', trans, '--add', it.link].execute().text)
            } catch (IOException e) {
                log.error (e.getMessage());
            }
        }
        log.debug("Finished")
    }
    
    private void signoff(String sid, String id) {
        def message = """{
            \"sid\": \"$sid\",
            \"op\": \"updateArticle\",
            \"article_ids\": \"$id\",
            \"mode\": \"0\",
            \"field\": \"2\"
           }"""
        def text = doPost(message).getText()
        log.debug ("Marking as read article $id: $text")
    }
    
    private Object doPost(String msg) {
        def post = new URL(ttrss).openConnection();
        post.setRequestMethod("POST")
        post.setDoOutput(true)
        post.setRequestProperty("Content-Type", "application/json")
        post.getOutputStream().write(msg.getBytes("UTF-8"));
        def postRC = post.getResponseCode();
        if(postRC.equals(200)) {
            return post.getInputStream()
        }
        else {
            log.warn ("POST response status was $postRC")
            return null;
        }
    }
}

@groovy.transform.Canonical
public class Article {
    int id
    String title
    String link
    
    boolean equals(Object other) {
        if (other instanceof Article) {
            def t0 = this.title.replaceAll("\'", "").toLowerCase()
            def t1 = ((Article)other).title.replaceAll("\'", "").toLowerCase()
            return t0 == t1
        }
    }
}

def process = new Process()
def sid = process.login()
def entries = process.getArticles(sid)
process.transmit(sid, entries);
