import os
import re
import math

# TODO: add leader/nonleader analysis
# TODO: add combined times

# These are the timestamps of the first requests to the server
#       ordered by their run number
# The last value is the end of the last request of the last run
RUNTIMES = [1336840909254,
            1336842780000,
            1336843992709,
            1336845898135,
            1336847014221]

# the ith element is about run i (the ith time server restarts)

diploma_take_latency = [[], [], [], []]
diploma_takeNum = [0, 0, 0, 0]
diploma_takeGood = [0, 0, 0, 0]
diploma_takeGood_nonleader = [0, 0, 0, 0]
diploma_takeBad = [0, 0, 0, 0] 

diploma_get_latency = [[], [], [], []]
diploma_getNum = [0, 0, 0, 0]
diploma_getGood = [0, 0, 0, 0]
diploma_getGood_nonleader = [0, 0, 0, 0]
diploma_getBad = [0, 0, 0, 0] 

diploma_cloudreq = [0, 0, 0, 0] 

timeoutPeriod = 6000

# different algorithm than experiment 5
def runAssign(time):
    for i in range(len(RUNTIMES)-1):
        if RUNTIMES[i] > time:
            return i-1
    return 3

# return only the first line of the file
def head(f):
  stdin,stdout = os.popen2("head -n 1 "+f)
  stdin.close()
  line = stdout.readlines(); stdout.close()
  return line

# return only the last line of the file
def tail(f):
  stdin,stdout = os.popen2("tail -n 1 "+f)
  stdin.close()
  line = stdout.readlines(); stdout.close()
  return line

def dirWalk(dirname):

    global diploma_take_latency
    global diploma_takeNum 
    global diploma_takeGood
    global diploma_takeGood_nonleader
    global diploma_takeBad

    global diploma_get_latency
    global diploma_getNum 
    global diploma_getGood
    global diploma_getGood_nonleader
    global diploma_getBad

    global diploma_cloudreq

    diploma_take_latency = [[], [], [], []]
    diploma_takeNum = [0, 0, 0, 0]
    diploma_takeGood = [0, 0, 0, 0]
    diploma_takeGood_nonleader = [0, 0, 0, 0]
    diploma_takeBad = [0, 0, 0, 0] 

    diploma_get_latency = [[], [], [], []]
    diploma_getNum = [0, 0, 0, 0]
    diploma_getGood = [0, 0, 0, 0]
    diploma_getGood_nonleader = [0, 0, 0, 0]
    diploma_getBad = [0, 0, 0, 0] 

    diploma_cloudreq = [0, 0, 0, 0] 

    for (path, dirs, files) in os.walk(dirname):
        for filename in files:
            # skip cloud files
            if filename.startswith("csm_cld"):
                continue
            
            # get the last line of file
            tailine = tail(os.path.join(path, filename))
            tailtime = int(tailine[0].strip()[:13])

            # Continue if it's an old file 
            if tailtime < RUNTIMES[0]:
                continue

            # Put the files into run buckets based on tail timestamp
            runNumber = runAssign(tailtime) 
            #print runNumber, tailtime, path, filename
           
            ###################################################
            # get latency from the middle of the files
            tmp_latency = 0
            for line in open(os.path.join(path, filename)):
                # get cloud access counts
                # takeLeadership, releaseLeadership, uploadState
                #
                # takeLeadership 
                clouds0 = re.search("leader to cloud hearbeat", line)
                if clouds0 is not None:
                    diploma_cloudreq[runNumber] += 1
                clouds1 = re.search("trying to take leadership ", line)
                if clouds1 is not None:
                    diploma_cloudreq[runNumber] += 1
                # releaseLeadership
                clouds2 = re.search("released leadership to cloud in ", line)
                if clouds2 is not None:
                    diploma_cloudreq[runNumber] += 1
                # both releaseLeadership and uploadState
                clouds3 = re.search("no LEADER_CONFIRM_ACK, uploaded state to cloud in", line)
                if clouds3 is not None:
                    diploma_cloudreq[runNumber] += 2
                clouds4 = re.search("onStop released leadership to cloud", line)
                if clouds4 is not None:
                    diploma_cloudreq[runNumber] += 2
                clouds5 = re.search("old region empty, uploaded state to cloud ", line)
                if clouds5 is not None:
                    diploma_cloudreq[runNumber] += 2
                # end of cloud access counts

                # Number of takes clicked
                take_search = re.search("Clicked take picture button", line)
                if take_search is not None:
                    diploma_takeNum[runNumber] += 1
                # Number of gets clicked
                get_search = re.search("making GET photo PACKET to send to the leader. Requesting for region", line)
                if get_search is not None:
                    diploma_getNum[runNumber] += 1

                # Photo latencies are recorded before we know if there was a success
                # so save the latencies temporarily in tmp_latency and 
                # if there is a success, save it in the array
                
                # TODO: add leader/nonleader analysis
                # Retrieve latency info
                lat = re.search("(.*)leader (.*) photo latency = (.*)", line)
                if lat is not None:
                    # reset flags
                    tmp_latency = int(lat.group(3))

                # t4. successfulness search
                # have checked no double bad counts (i.e. from multiple server replies of same request)
                tbad = re.search("one more takeBad", line)
                if tbad is not None:
                    diploma_takeBad[runNumber] += 1
                gbad = re.search("one more getBad", line)
                if gbad is not None:
                    diploma_getBad[runNumber] += 1

                tgood = re.search("one more takeGoodSave", line)
                if tgood is not None:
                    diploma_takeGood[runNumber] += 1
                    diploma_take_latency[runNumber].append(tmp_latency)
                ggood = re.search("one more getGood", line)
                if ggood is not None:
                    diploma_getGood[runNumber] += 1
                    diploma_get_latency[runNumber].append(tmp_latency)

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
    #print "-- Latency: raw, including timed outs --"
    #print "number:\t\t%d" % len(latency_list)
    #print "mean:\t\t%d ms" % (sum(latency_list)/len(latency_list))
    #print "stdv:\t\t%d ms" % stdvCalc(latency_list)
    latency_list_sorted = sorted(latency_list)
    #print "median:\t\t%d ms" % latency_list_sorted[len(latency_list)/2]
    #print "range:\t\t%d ms ~ %d ms" % (latency_list_sorted[0], latency_list_sorted[-1])
    print "-- Latency associated with successes --"
    latency_list_small = listWithinTimeout(latency_list_sorted)
    print "number:\t\t%d\texcluding %d requests that have replies > timeout" % (len(latency_list_small), len(latency_list)-len(latency_list_small))
    print "mean:\t\t%d ms" % (sum(latency_list_small)/len(latency_list_small))
    print "stdv:\t\t%d ms" % stdvCalc(latency_list_small)
    print "median:\t\t%d ms" % latency_list_small[len(latency_list_small)/2]
    print "range:\t\t%d ms ~ %d ms" % (latency_list_small[0], latency_list_small[-1])

def printResults():
    for iRun in range(len(RUNTIMES)-1):
        print
        print "======= TAKEs for run %d ========" % (iRun+1)
        print "clicked:\t%d" % diploma_takeNum[iRun]
        print "successes:\t%d\t%d %%" % (diploma_takeGood[iRun], (diploma_takeGood[iRun]*100/diploma_takeNum[iRun]))
        print "fails:\t\t%d" % diploma_takeBad[iRun]
        print "timed outs:\t%d\tcalculated indirectly by number-successes-fails" % (diploma_takeNum[iRun]-diploma_takeGood[iRun]-diploma_takeBad[iRun])
        # only do latency when there are replies
        if (diploma_takeGood[iRun] + diploma_takeBad[iRun]) > 0:
            latencyPrints(diploma_take_latency[iRun])

        print
        print "------- GETs for run %d --------" % (iRun+1)
        print "clicked:\t%d" % diploma_getNum[iRun]
        print "successes:\t%d\t%d %%\tincluding existing regions without a photo" % (diploma_getGood[iRun], (diploma_getGood[iRun]*100/diploma_getNum[iRun]))
        print "fails:\t\t%d\tdue to null region, but still with reply" % diploma_getBad[iRun]
        print "timed outs:\t%d\tcalculated indirectly by number-successes-fails" % (diploma_getNum[iRun]-diploma_getGood[iRun]-diploma_getBad[iRun])
        # only do latency when there are replies
        if (diploma_getGood[iRun] + diploma_getBad[iRun]) > 0:
            latencyPrints(diploma_get_latency[iRun])
    
        print
        print "----- Cloud accesses for run %d ----" % (iRun+1)
        print "cloudreq:\t%d. These are from heartbeats and the few take leaderships (at beginning of run) only! Since the phones were not mobile" % diploma_cloudreq[iRun]
        print "totalreq:\t%d\t sum of TAKEs and GETs" % (diploma_takeNum[iRun]+diploma_getNum[iRun])
        print "ratio:\t\t1 : %f\t ratio of cloudreq : totalreq. Note that cloud requests are NOT counted in the total requests" % ((diploma_takeNum[iRun]+diploma_getNum[iRun])/(1.0*diploma_cloudreq[iRun]))
        print

if __name__ == "__main__":
    print "about 10 seconds to run"
    dirname = "logs_sorted_bytime"
    dirWalk(dirname)
    print
    print "#############################################"
    print "###### Diploma based on run number ##########"
    print "#############################################"
    printResults()
