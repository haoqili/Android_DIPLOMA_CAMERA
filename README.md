This project is going to be modifying Jason's DIPLOMAMatrix that does sparse matrix multiplication into an app that shares images taken from Android's cameras.

Checklist
=========

0. Make sure 4G/3G is on
0. Make sure the IP addresses of the phones are all different
1. SMCloud restart server running ?
2. DIPLOMA restart server running ?
3. Ensure Items 1 and 2 above do not run on the same port on the same machine.
4. The code on the phone must point to the right server in both cases
1 and 2 above.
5. Turn on Sound, Turn on GPS, Brightness, USB Debugging,
6. Turn off rotation
7. Turn on GPS
8. Clear log
7. Clear log files from sdcard of all phones.
8. Make sure logs are flushed on to the sdcard immediately so that we don't end up with empty logs at the end of an experiment.

Procedure
========

1. Start Barnacle

2. Must first set region. 
Note: When setting regions on multiple phones, do it one at a time or else they would crash due to concurrency issues.

3. "Take a Photo!"

4. If successful, should observe: `I/UserApp (  ..): ....: Upload Photo succeeded`

4. Get photos from one of the 6 regions

TO ASK JASON
=========
* Why is there a cloud communication when a leader switches regions?

* Why does UserApp's handleDSMRequest have the comment "Handle and reply to a DSM Atom request on the local region."?

Troubleshooting
============

* Fatal exception in "Set Region" and "Set" is caused by not putting in anything in the textboxes

* I set 2 phones to region 1, but they both claim to be leaders of region 1!!  (They can send and receive UDP packets between themselves)
    
    You cannot set phones to the same region concurrently, or else they would be both leaders.
//
