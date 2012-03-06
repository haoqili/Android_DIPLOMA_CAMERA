This project is going to be modifying Jason's DIPLOMAMatrix that does sparse matrix multiplication into an app that shares images taken from Android's cameras.

TO ASK JASON
=========
* Why is there a cloud communication when a leader switches regions?

* Why does UserApp's handleDSMRequest have the comment "Handle and reply to a DSM Atom request on the local region."?

Troubleshooting
============

* Fatal exception in "Set Region" and "Set" is caused by not putting in anything in the textboxes

* I set 2 phones to region 1, but they both claim to be leaders of region 1!!  (They can send and receive UDP packets between themselves)
    
    You cannot set phones to the same region concurrently, or else they would be both leaders.


