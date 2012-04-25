This project is going to be modifying Jason's DIPLOMAMatrix that does sparse matrix multiplication into an app that shares images taken from Android's cameras.

Checklist
=========

WHAT IP ADDRESS WILL WE HAVE??

* __GPS on__
* Sound on

Settings
Display: 
* __Auto-rotate screen OFF__
* Screen timeout: 10min
Applications, Development
* USB debbugging on

Barnacle
* __IP address different__

0. Make sure 4G/3G is on
1. SMCloud restart server running ?
2. DIPLOMA restart server running ? on different port?
4. The code on the phone must point to the right server in both cases
1 and 2 above.

8. Clear log
7. Clear log files from sdcard of all phones.

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
