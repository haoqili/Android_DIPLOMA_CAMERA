Run 1

* 12:39 - 11:07am. Duration: 28minutes
* 4G
* Faster cloud get latency due to phone caching, because we pressed all the gets one after the other on one phone, i.e: 
    Phone 1 Get 1, Phone 1 Get 2, Phone 1 Get 3 .... Phone 2 Get 1, Phone 2 Get 2, Phone 2 Get 3 ...
* Sequence

    * Take real pics, and get all pictures
    * Take black pics, and get all pictures
    * Take real pics, and get all pictures
    * Take black pics, and get all pictures

Run 2

* Server restart 1:13, taking pictures start: 1:17. End: 1:23 ish. Duration 6-11 minutes, let's say, 10
* 3G
* Faster cloud get latency due to phone caching, because we pressed all the gets one after the other on one phone, i.e: 
    Phone 1 Get 1, Phone 1 Get 2, Phone 1 Get 3 .... Phone 2 Get 1, Phone 2 Get 2, Phone 2 Get 3 ...
    Until the very last few gets when we discovered the method that avoids phone caching (see below)
* Sequence

    * Take real pics, and get all pictures
    * Take black pics, and get all pictures
    * At end, could not get due to leader in region 0 getting GPS fixes so it changed to a dormant region. So we stopped the run immediately and turned off the GPS on all phones

* The server logs for this run is not logged

Run 3

* 1:32 - 1:58. Duration: 26 minutes
* 3G
* Normal cloud get latency without phone caching, because we left enough gap between gets on each phone:
    Phone 1 Get 1, Phone 2 Get 1, Phone 3 Get 1 .... Phone 1 Get 2, Phone 2 Get 2, Phone 3 Get 2 ...
* Sequence

    * Take real pics, and get all pictures
    * Take black pics, and get all pictures
    * Take black pics, and get all pictures
    * Take black pics, and get all pictures

* Anirudh had some bad gets, but when he tried again, they worked. Maybe that phone was going through a barnacle app breakup, or the wifi was not good.

Run 4
    
* 2:03 - 2:24. Duration: 21 minutes
* 4G
* Everything same as Run 3
