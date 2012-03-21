General
========
Logs are named by time stamp. 

The experiment did not start until you see the line:
`location CHANGED TO NEW region = (1,0) from region = (-1,-1)`

If the app crashed, you have to find that line again to determine when to start reading data

You can request picture from any region, even if you are not in a valid region.

TO CHANGE
=======
* For requesting pictures, log which region the request goes to
* Log all packet size to/from server
* Add in server side return status for request picture
* Don't log the photo bytes in Cloud take new pic!

UI Mistakes
=======
* Disallow user setting regions themselves
* Take picture and Get photo buttons  only show for valid regions (1-6)
* A timeout for button after pressed once (to wait for network)
* Better display to user (not in log) of when photo data is nul (region doesn't have a photo)

Errors
======

* HTTP Post failed to be executed in request picture (twice)

Logcats
======

a
====

1714008
-------

* Requested and photo data null  `1331843842302: request picture latency = 1379`
* Took picture at region 1 `1331843872890: sendClientNewpic latency = 14288`
* Requested and showed downloaded photo `1331843913863: request picture latency = 8776`

ONPAUSE - only see GPS locationchanged before, so reason why it paused is unclear
    
    1331844059568: location PINPOINTS to region = 0.0, previous 0
    1331844059568: location is INSIDE BOUNDARY, stay at prev region = (0,0)
    1331844065803: HI I'm in ONPAUSE()

4343917 (common in small-sized logs)
--------

Resumed in region 4 (but still assigned to region -1 due to my bug)

    1331844343930: *** Opened log file for writing ***
    1331844343931: *** Application started ***
    1331844343931: HI I'm in ONRESUME()

Sudden stop - unclear why

    1331845691094: .......... GPS onLocationChanged ...... 
    1331845 

b
======

2800555
------

* r1 `1331843876954: request picture latency = 5864`
* r1 `1331843909563: request picture latency = 25092` 
* Requested and photo data null `1331843945097: request picture latency = 2791`
* Took picture at region 3 `1331844027014: sendClientNewpic latency = 16236`
* r0 `1331844225758: request picture latency = 10555` 

4371048
-------

* r0 `1331844402551: request picture latency = 2763`

4441802
-----

* r1 `1331844553760: sendClientNewpic latency = 3889`
* r0 `1331844591288: request picture latency = 2596`
* r1 `1331844636883: sendClientNewpic latency = 8913`
* r1 `1331844657280: sendClientNewpic latency = 8999`
* r2 `1331844706172: request picture latency = 2894`
* r2 `1331844749589: request picture latency = 2750`
* r3 `1331844801455: sendClientNewpic latency = 15710`

Four back to back picture requests

    1331844591346: stay at region 0
    1331844591349: Trying to get photo from server
    1331844591349: Server request to url: http://128.30.87.130:6212/102/3/0/
    1331844591354: gson string: {"debugstr":"today is pi day","status":0}
    1331844591355: about to execute HTTP POST
    1331844592022: finished executing HTTP POST, get data
    1331844592024: make input stream reader for data
    1331844592025: Make new Gson
    1331844592027: Returning cloud object
    1331844592124: request picture latency = 775
    1331844592124: RETURN STATUS: 0
    1331844592154: Show downloaded photo
    1331844592255: Trying to get photo from server
    1331844592256: Server request to url: http://128.30.87.130:6212/102/3/0/
    1331844592259: gson string: {"debugstr":"today is pi day","status":0}
    1331844592259: about to execute HTTP POST
    1331844592637: finished executing HTTP POST, get data
    1331844592638: make input stream reader for data
    1331844592642: Make new Gson
    1331844592667: Returning cloud object
    1331844592862: request picture latency = 606
    1331844592862: RETURN STATUS: 0
    1331844592899: Show downloaded photo
    1331844592903: Trying to get photo from server
    1331844592903: Server request to url: http://128.30.87.130:6212/102/3/0/1331844592922: gson string: {"debugstr":"today is pi day","status":0}
    1331844592923: about to execute HTTP POST1331844593551: finished executing HTTP POST, get data
    1331844593552: make input stream reader for data
    1331844593584: Make new Gson 
    1331844593599: Returning cloud object 
    1331844593699: request picture latency = 796
    1331844593699: RETURN STATUS: 0
    1331844593726: Show downloaded photo
    1331844593730: Trying to get photo from server
    1331844593730: Server request to url: http://128.30.87.130:6212/102/3/0/
    1331844593745: gson string: {"debugstr":"today is pi day","status":0}
    1331844593746: about to execute HTTP POST1331844594421: finished executing HTTP POST, get data
    1331844594423: make input stream reader for data1331844594424: Make new Gson
    1331844594435: Returning cloud object
    1331844594660: request picture latency = 930
    1331844594660: RETURN STATUS: 0
    1331844594692: Show downloaded photo

d
====

2263832
------

* r0 `1331843875777: request picture latency = 9434`
* r1 `1331843898368: sendClientNewpic latency = 15421`

3913552
-----

* r0 `1331844003488: request picture latency = 2538` data null
* r1 `1331844229737: sendClientNewpic latency = 3756`
* r1 `1331844230106: request picture latency = 287` data null
* r1 `1331844249006: sendClientNewpic latency = 7990`
* r0 `1331844365684: request picture latency = 2771` data null
* r0 `1331844380176: request picture latency = 1348`
* r0 `1331844380918: request picture latency = 695` back to back

4424674
-----

* r0 `1331844480122: request picture latency = 3234`
* r0 `1331844572127: request picture latency = 2071` data null
* r0 `1331844588353: request picture latency = 2179` data null

e
=====

2677385
------

* r1 `1331843873598: sendClientNewpic latency = 10266`
* r1 `1331843894120: sendClientNewpic latency = 10964`
* r1 `1331843911793: sendClientNewpic latency = 11498`
* r1 `1331843936311: sendClientNewpic latency = 8878`
* r1 `1331844023084: sendClientNewpic latency = 18328`

4063398
------

* r-1 but at r2 `1331844107414: request picture latency = 2489`
* r-1 but at r2 `1331844108052: request picture latency = 393` back to back
* r-1 but at r2 `1331844114594: request picture latency = 2596`
* r-1 but at r2 `1331844135834: request picture latency = 17161`
* r1 ` 1331844422570: sendClientNewpic latency = 11168`
* r1 `1331844458454: sendClientNewpic latency = 9044`
* r1 `1331844484964: sendClientNewpic latency = 4206`
* r1 `1331844499750: request picture latency = 2343`
* r1 `1331844537486: sendClientNewpic latency = 5166`
* r1 ` 1331844640483: sendClientNewpic latency = 7548`
* r1 `1331844645954: sendClientNewpic latency = 3281`

4678624
-------

* r2 `1331844793031: sendClientNewpic latency = 15633`

f
======

1712775
-------

* r0 `1331843835977: request picture latency = 2391` data null
* r1 `1331843857245: request picture latency = 2408` data null
* r1 `1331843875923: sendClientNewpic latency = 9565`
* r1 `1331843887940: request picture latency = 10369` data null
* r1 `1331843923440: sendClientNewpic latency = 10940`
* r1 `1331843929553: request picture latency = 5076`
* r1 `1331843956316: sendClientNewpic latency = 11383`

4066875
-------

* r1 `1331844126228: sendClientNewpic latency = 11197`
* r1 `1331844129497: request picture latency = 546` data null

4157310
-----

Crash right after picture returned with okay status

* r1 `1331844233589: sendClientNewpic latency = 20422`

4243055
------

* r1 `1331844289303: sendClientNewpic latency = 13220`
* r1 `1331844361347: sendClientNewpic latency = 12325`
* r1 `1331844364111: request picture latency = 2677` data null
* r1 `1331844364905: request picture latency = 742` data null, back to back
* r1 `1331844383829: sendClientNewpic latency = 3749`
* r1 `1331844396437: request picture latency = 2287` data null
* r1 ` 1331844454305: sendClientNewpic latency = 8931`
* r1 `1331844457273: request picture latency = 806` data null

4514098
------

* r1 `1331844608970: sendClientNewpic latency = 4177`
* r1 `1331844611210: request picture latency = 336` data null

4661438
------

* r1 `1331844724332: sendClientNewpic latency = 13974`
* r1 `1331844729835: request picture latency = 1755` data null
* r1 `1331844769111: sendClientNewpic latency = 13085`
* r1 `1331844786549: request picture latency = 15285` data null
* r2 `1331844821747: sendClientNewpic latency = 14233`
* r1 `1331844724332: sendClientNewpic latency = 13974`
* r1 `1331844729835: request picture latency = 1755` data null
* r1 `1331844769111: sendClientNewpic latency = 13085`
* r1 `1331844786549: request picture latency = 15285` data null
* r1 `1331844821747: sendClientNewpic latency = 14233` 

HTTP Post failed to be executed, happened in middle of logfile, so didn't cause crash.

    1331844823319: stay at region 2
    1331844823597: Trying to get photo from server
    1331844823616: Server request to url: http://128.30.87.130:6212/102/6/0/
    1331844823646: gson string: {"debugstr":"today is pi day","status":0}
    1331844823646: about to execute HTTP POST
    1331844830202: .......... GPS onLocationChanged ......

    this is missing:
    
    1331844724315: finished executing HTTP POST, get data
    1331844724316: make input stream reader for data

g
====

1715410
-------

* r0 `1331843818207: request picture latency = 2531` data null
* r1 `1331843835343: sendClientNewpic latency = 3259`
* r1 `1331843842441: request picture latency = 3576`
* r1 `1331843853024: request picture latency = 2492` data null
* r1 `1331843874389: sendClientNewpic latency = 7798`
* r0 `1331843958505: request picture latency = 3462` 
* r1 `1331844027911: request picture latency = 2266` data null
* r1 `1331844056032: request picture latency = 2638`

4146805
------

* r0 `1331844167791: request picture latency = 2679`
* r0 `1331844182016: request picture latency = 2947`
* r1 `1331844211332: request picture latency = 2487`
* r1 `1331844342666: sendClientNewpic latency = 10169`
* r1 `1331844344425: request picture latency = 635`
* r1 `1331844365297: sendClientNewpic latency = 10735`
* r0 `1331844413168: request picture latency = 2997`
* r1 `1331844440032: request picture latency = 2490`
* r1 `1331844486423: sendClientNewpic latency = 4937`
* r1 `1331844521919: request picture latency = 2695`

4620347
------

Stopped while printing out new photo bytes

4724324
------

* r1 `1331844784672: sendClientNewpic latency = 14014`
* r1 `1331844827303: sendClientNewpic latency = 14871`

h
======

1713097
--------

* r1 `1331843928125: sendClientNewpic latency = 9144`
* r1 `1331843977580: sendClientNewpic latency = 8127`
* r1 `1331843986473: sendClientNewpic latency = 6676`

4014220
--------

* r1 `1331844181506: sendClientNewpic latency = 3534`
* r1 `1331844212210: request picture latency = 2480`
* r2 `1331844225651: request picture latency = 2416` data null

4256804
--------

* r1 `1331844406935: request picture latency = 3446`
* r1 `1331844437901: request picture latency = 2828`
* r1 `1331844460942: sendClientNewpic latency = 14694`
* r2 `1331844461373: request picture latency = 264` data null
* r2 `1331844461806: request picture latency = 426` data null back to back 
* r2 `1331844462470: request picture latency = 657` data null back to back

4480807
--------
* r1 `1331844606115: sendClientNewpic latency = 2101`
* r1 `1331844617843: request picture latency = 2269` data null
* r1 `1331844639036: request picture latency = 8243`
* r2 `1331844659642: sendClientNewpic latency = 10325`
* r2 `1331844665936: request picture latency = 1675` data null
* r3 `1331844685605: sendClientNewpic latency = 4162`
* r3 `1331844727235: sendClientNewpic latency = 8426`

HTTP Post failed to be executed, happened in middle of logfile, so didn't cause crash.

    1331844694729: Trying to get photo from server
    1331844694730: Server request to url: http://128.30.87.130:6212/102/3/0/1331844694736: gson string: {"debugstr":"today is pi day","status":0}
    1331844694737: about to execute HTTP POST

i
===

3739066
-------

* r0 `1331843857143: request picture latency = 2849` data null
* r1 `1331843885039: sendClientNewpic latency = 9652`
* r1 `1331843906657: request picture latency = 21537`
* r1 `1331843912771: request picture latency = 6102` back to back


3958099
-------

* r0 `1331843977567: request picture latency = 7800` data null
* r1 `1331844024874: request picture latency = 20243` data null
* r2 `1331844129277: sendClientNewpic latency = 3535`
* r2 `1331844130043: request picture latency = 656` 
* r2 `1331844130838: request picture latency = 768` back to back
* r2 `1331844158136: sendClientNewpic latency = 3220`
* r1 `1331844401852: sendClientNewpic latency = 4825`
* r1 `1331844569452: sendClientNewpic latency = 3175` 
* r1 `1331844606787: request picture latency = 3352`
* r1 `1331844630608: sendClientNewpic latency = 11329`

j
=====

4091559
-------

* r1 `1331844157264: sendClientNewpic latency = 2866`
* r1 `1331844224320: sendClientNewpic latency = 10795`
* r1 `1331844238916: sendClientNewpic latency = 10032`
* r1 `1331844252579: sendClientNewpic latency = 8200` 
* r1 `1331844258407: request picture latency = 2164`
* r1 `1331844277639: request picture latency = 4607` data null
* r2 `1331844291937: sendClientNewpic latency = 3545`
* r2 `1331844301738: request picture latency = 3405`
* r2 `1331844320651: sendClientNewpic latency = 3624`
* r2 `1331844322624: request picture latency = 319` data null
* r2 `1331844345906: sendClientNewpic latency = 3604`
* r1 `1331844535624: sendClientNewpic latency = 8216`

4585077
-------

* r1 `1331844627380: sendClientNewpic latency = 10621`

4708270
-------

* r1 `1331844810223: sendClientNewpic latency = 23334`

