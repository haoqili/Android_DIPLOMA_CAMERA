Run 1

    * 4G
    * Faster cloud get latency due to phone caching, because we pressed all the gets one after the other on one phone, i.e: 
        Phone 1 Get 1, Phone 1 Get 2, Phone 1 Get 3 .... Phone 2 Get 1, Phone 2 Get 2, Phone 2 Get 3 ...
    * Sequence

        * Take real pics, and get all pictures
        * Take black pics, and get all pictures
        * Take real pics, and get all pictures
        * Take black pics, and get all pictures

Run 2
    
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

    * 3G
    * Normal cloud get latency without phone caching, because we left enough gap between gets on each phone:
        Phone 1 Get 1, Phone 2 Get 1, Phone 3 Get 1 .... Phone 1 Get 2, Phone 2 Get 2, Phone 3 Get 2 ...
    * Sequence

        * Take real pics, and get all pictures
        * Take black pics, and get all pictures
        * Take black pics, and get all pictures
        * Take black pics, and get all pictures

Run 4
    
    * 4G
    * Everything same as Run 3
