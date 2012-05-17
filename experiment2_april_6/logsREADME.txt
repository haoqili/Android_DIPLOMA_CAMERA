1. I don't understand the nexus_s portion of the logs. There are 10
folders . Are these 10 pairs of phones , since each folder has 2 sub
folders ? Also what do the numbers "303", "363" , "393" etc on the
subfolders actually mean ?
There are 2 sub folders corresponding to the two phones in that pair. The ### are the first 3 digits of that phone's ID (its sequence when you type "adb devices"). You should see the full ID when you cd into that folder and find the [full sequence].txt

2. Can we get the actual time stamps of the logs by looking at the
phones' file system, so that we can separate logs corresponding to
different runs of our experiments ? Worst case we can look for large
differences in the UNIX time stamp.

I'll see how easy it is to get them from the UNIX times from the logs.

Btw, how many experiments did we do ? I guess 3 :

1. Nexus S
2. Notes.
3. Re run with Nexus S where we positioned people at specific regions apriori

Jason suggests that we only use the last 2 (discarding the first one before we restarted the server.)

========



J: We didn't pass out the white notes in the experiment, so they weren't used. Just the 20 black notes.
H: Hmm I only got 18 Galaxy Note phones, were there 2 that were not in the drawers?
J: Two are in my drawer.



