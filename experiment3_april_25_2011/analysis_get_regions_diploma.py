import os
import re
import math

# add region width
# leader vs nonleader

# each of the data is now an array of 6 elements
# where the ith element is about requests made i regions apart

numRegions = 6
diploma_get_latency = [[], [], [], [], [], []]
diploma_getNum = [0, 0, 0, 0, 0, 0]
# TODO: add the picture sizes of getGood
diploma_getGood = [0, 0, 0, 0, 0, 0]
diploma_getGood_nonleader = [0, 0, 0, 0, 0, 0]
diploma_getBad = [0, 0, 0, 0, 0, 0] 

timeoutPeriod = 6000

def regionHops(a, b):
    hops = b-a if a < b else a-b
    return hops

def dirWalk(dirname):

    global diploma_get_latency
    global diploma_getNum 
    global diploma_getGood
    global diploma_getGood_nonleader
    global diploma_getBad

    diploma_get_latency = [[], [], [], [], [], []]
    diploma_getNum = [0, 0, 0, 0, 0, 0]
    diploma_getGood = [0, 0, 0, 0, 0, 0]
    diploma_getGood_nonleader = [0, 0, 0, 0, 0, 0] 
    diploma_getBad = [0, 0, 0, 0, 0, 0] 

    for (path, dirs, files) in os.walk(dirname):
        for filename in files:
            # a_region --> b_region
            a_region = -1
            b_region = -1
            isLatencyOk = False
            hasResultBad = False
            # get latency from the middle of the files
            for line in open(os.path.join(path, filename)):
                
                # go to next file if it's a camera cloud app file
                cloud_search = re.search("Cloud server request", line)
                if cloud_search is not None:
                    break

                # figure out a --> b regions
                # b is recorded first

                # t1. b_search
                b_search = re.search("(.*):.*Requesting for region: (.*) ", line)
                if b_search is not None:
                    b_region = int(b_search.group(2))
                    # reset isLatencyOk
                    isLatencyOk = False
                    hasResultBad = False

                # t2. a_search
                a_search = re.search("(.*): Client about to send CLIENT_DOWNLOAD_PHOTO.* Client in region: (.*) Client nodID: (.*)", line) 
                if a_search is not None:
                    diploma_getNum[regionHops(a_region,b_region)] += 1
                    a_region = int(a_search.group(2))

                # if no reply from leader, below are not reached
                # t3 leader/nonleader & latency search
                g = re.search("(.*): (.*) download remote photo latency = (.*)", line)
                if g is not None:
                    get_latency = int(g.group(3))
                    # there is a case when the app might got paused
                    # and start latency didn't get set
                    # so it records latency as "1335382548303" and "1335382549047"
                    # I have checked that all other latencies are:
                    if get_latency < 1000000:
                        isLatencyOk = True
                        diploma_get_latency[regionHops(a_region,b_region)].append(get_latency)

                # t4. successfulness search only when isLatencyOk
                if isLatencyOk:

                    if not hasResultBad:
                        # only search a bad when there has been no bad replies
                        # so that we don't double count bad replies from a single request
                        # see extra_notes/analysis_get_regions_diploma_1.txt for more info
                        sbad = re.search("(.*): .*because DSM Layer on originator leader", line)
                        if sbad is not None:
                            hasResultBad = True
                            diploma_getBad[regionHops(a_region,b_region)] += 1

                    sgood1 = re.search("(.*): PHOTO DATA is NULL, because region doesn't have a photo yet", line)
                    if sgood1 is not None:
                        diploma_getGood[regionHops(a_region,b_region)] += 1

                    sgood2 = re.search("(.*): Remote photo's length: (.*)", line)
                    if sgood2 is not None:
                        diploma_getGood[regionHops(a_region,b_region)] += 1

def stdvCalc(list):
    cumu = 0
    mean = sum(list)/len(list)
    for item in list:
        cumu += (mean-item)*(mean-item)
    return math.sqrt(cumu/len(list)) 

def listWithinTimeout(sortedList):
    global timeoutPeriod
    listWithin = sortedList[:] # make a copy first
    firstTimeoutI = len(sortedList)-1 #initialize to the biggest element
    for i in range(len(sortedList)):
        if sortedList[i] >= timeoutPeriod:
            # this is the first element bigger than timeoutPeriod
            return listWithin[:i]
    # all elements in list are smallerthan timeout
    return listWithin

def latencyPrints(latency_list):
    print "-- Latency: raw, including timed outs --"
    print "number:\t\t%d" % len(latency_list)
    print "mean:\t\t%d ms" % (sum(latency_list)/len(latency_list))
    print "stdv:\t\t%d ms" % stdvCalc(latency_list)
    latency_list_sorted = sorted(latency_list)
    print "median:\t\t%d ms" % latency_list_sorted[len(latency_list)/2]
    print "range:\t\t%d ms ~ %d ms" % (latency_list_sorted[0], latency_list_sorted[-1])
    print "-- Latency: excluding timed outs --"
    latency_list_small = listWithinTimeout(latency_list_sorted)
    print "number:\t\t%d\tis %d %% of all latencies.\t%d requests never have responses" % (len(latency_list_small), (len(latency_list_small)*100/(1.0*(len(latency_list)))), len(latency_list)-len(latency_list_small))
    print "mean:\t\t%d ms" % (sum(latency_list_small)/len(latency_list_small))
    print "stdv:\t\t%d ms" % stdvCalc(latency_list_small)
    print "median:\t\t%d ms" % latency_list_small[len(latency_list_small)/2]
    print "range:\t\t%d ms ~ %d ms" % (latency_list_small[0], latency_list_small[-1])

def printResults():
    for iHop in range(numRegions):
        print
        print "======= GETs for hop %d ========" % iHop
        print "number:\t\t%d" % diploma_getNum[iHop]

        if diploma_getNum[iHop] > 0:
            print "successes:\t%d\t%d %%\tincluding existing regions without a photo" % (diploma_getGood[iHop], (diploma_getGood[iHop]*100/diploma_getNum[iHop]))
            print "fails:\t\t%d due to null region, but still with reply" % diploma_getBad[iHop]
            print "~timed outs:\t%d calculated indirectly by number-successes-fails" % (diploma_getNum[iHop]-diploma_getGood[iHop]-diploma_getBad[iHop])

            # only do latency when there are replies
            if (diploma_getGood[iHop] + diploma_getBad[iHop]) > 0:
                latencyPrints(diploma_get_latency[iHop])
            else:
                print "no latency calculations since no there are no replies"
        else: 
            print "no requests means no analysis"

if __name__ == "__main__":
    print "#############################################"
    print "###### Diploma Gets based on hop number #####"
    print "#############################################"
    print "takes about 2 minutes to run for me"
    print
    dirname = "experiment3_logmsgs/nexus"
    dirWalk(dirname)
    print "============================="
    print "====== Diploma Nexus ========"
    print "============================="
    printResults()
    print
    print
    dirname = "experiment3_logmsgs/notes"
    dirWalk(dirname)
    print "============================="
    print "====== Diploma Notes ========"
    print "============================="
    printResults()
