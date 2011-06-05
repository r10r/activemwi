# Description

ActiveMWI is a mailbox waiting message notifier. It opens up a manager connection to the asterisk
and hooks onto the login event of a peer. When it receives a login event it looks for new
messages in the mailbox of the peer. If the mailbox contains any unread messages, 
then activemwi will originate a connection between the peer and the mailbox.

# Configuration

The location of the configuration file is
<pre>
  /etc/activemwi.properties
</pre>

The following properties are mandatory:

- server.ip
- manager.user
- manager.pass
- mbox.exten
- mbox.context

## Example Configuration

Assume you have the following configuration as:

<pre>
# IP where the asterisk server is running
server.ip = 172.16.123.222
# the TCP port of the asterisk manager server
# manager.port = 5038
# the manager connection user
manager.user = manager
# the manager connection user password
manager.pass = tekO9BNfS8J668TkZZLI7Z
 
## properties for call origination 
# the mailbox extension
mbox.exten = 9000
# the mailbox context
mbox.context = mailbox
# mbox.ring.timeout = 20000
# mbox.retry.inverval = 600000
# mbox.retry.max = 6
</pre>

Then the asterisk manager configuration `/etc/asterisk/manager.conf` would
look like the following:

<pre>
[general]
enabled = yes
port = 5038
bindaddr = 172.16.123.222

[activemwi]
secret=tekO9BNfS8J668TkZZLI7Z
permit=172.16.123.0/255.255.255.0
read=system
write=originate,reporting
</pre>

And the diaplan `/etc/asterisk/extension.conf` must have a matching context
with a matching extension. 

<pre>
[mailbox]
exten => 9000,1,VoiceMailMain(${CALLERID(num)},s)
</pre>

