# Careless

Careless is a script that will download articles from a specific Tiny Tiny RSS feed.

Tiny Tiny RSS is an RSS feed reader that you can install locally to assemble all your articles in a convenient place, much like Feedly and the former Google Reader. Many sites however publish only summaries or introductions over RSS, instead of the whole article. Careless is a Groovy script that makes use of Tiny Tiny RSS's exposed API to find the links inside and if they are magnet links download the article using Transmission.

Rename and change the file named `example.conf` and configure which directories, user, group and unix rights should be used:

    trans    The hostname of the computer where Transmission runs
    ttrss    The url to Tiny Tiny RSS, ending on "/api/"
    user     Your username on Tiny Tiny RSS
    pass     Your user's password on Tiny Tiny RSS
    feed     The number of the feed that contains the links
        
    dir      The directory under which the downloaded files are stored
    allowed  a list of allowed file types
    nrs      a regex matching with how the original filenames state their series and number.
    
You can run the script manually (use of `sudo` is to set the unix user, group and rights):

    $ sudo groovy careless.groovy properties.conf

but you shouldn't. The two `systemd` files will let you run the script automatically every hour and will make the script's log messages appear in the system logs. In `careless.service`, adapt the line starting with `ExecStart` to point it to where you install Careless. Put both files in `/etc/systemd/system/` and do:

    $ sudo systemctl start careless.timer
    $ sudo systemctl enable careless.timer

If your system doesn't use `systemd`, you can trigger Careless with inotify(7) or a cron job.


