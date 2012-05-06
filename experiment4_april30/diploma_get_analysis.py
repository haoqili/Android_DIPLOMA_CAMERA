# based on experiment3_april_25_2011/analysis_get_regions_diploma.py
import os
import re
import math

# TODO: 
# cloud
# takes/both
# look for repeated replies
# TODO: leader vs nonleader for gets
# gather real timed out numbers
# region to region

# These are the timestamps of the first requests to the server
#       ordered by their run number
# Only the first run have apps that started before the 1st timestamp
#       all the other runs have the timestamps first
# The second to last timestamp is estimated based on the last server request
#       of its previous run, because we lost the server logs for that trial
# One trial had a server restart without the cloud restarting, otherwise 
#       diploma and cloud restarted each new trial about the same time
#       but we can safely decouple the 2 experiments (only care about sums)
# The first value is a bit lower than the start of server for run 1 because 
#       that value is used to filter out old logs, but we also have some phones
#       that had the app on-pause for a littlewhile before the experiment
# The last value is the end of the last request of the last run
RUNTIMES = [1335794700000, 
            1335795747163, 
            1335796782086, 
            1335797351711, 
            1335798310999, 
            1335799140835]

# the ith element is about run i (the ith time diploma server restarts)

diploma_get_latency = [[], [], [], [], []]
diploma_getNum = [0, 0, 0, 0, 0]
diploma_getGood = [0, 0, 0, 0, 0]
diploma_getGood_nonleader = [0, 0, 0, 0, 0]
diploma_getBad = [0, 0, 0, 0, 0] 

timeoutPeriod = 6000

# The time provided should be the median (midtime) of a logmsg
#       because a logmsg's head or tail may spill a bit over to other runs
# 
# 5/csm_dip-1335792866157.txt and 17/1335792885337 return -1 
#       but we can ignore it since it is always onPause() 
#       so there are no clicks or getNums or anything useful in those two files
def runAssign(time):
    # There still might be some test files right before the experiment 
    #       that need to be discarded
    for i in range(len(RUNTIMES)-1):
        if RUNTIMES[i] > time:
            return i-1
    return 4

# return only the first line of the file
def head(f):
  stdin,stdout = os.popen2("head -n 1 "+f)
  stdin.close()
  line = stdout.readlines(); stdout.close()
  return line

# return only the last line of the file
# N.B. only 16/csm-1335770458741.txt does not have a complete 
#      last timestamp. It's ran before our experiments, so we
#      must first check the first timestamp is valid
def tail(f):
  stdin,stdout = os.popen2("tail -n 1 "+f)
  stdin.close()
  line = stdout.readlines(); stdout.close()
  return line

def dirWalk(dirname):

    global diploma_get_latency
    global diploma_getNum 
    global diploma_getGood
    global diploma_getGood_nonleader
    global diploma_getBad

    diploma_get_latency = [[], [], [], [], []]
    diploma_getNum = [0, 0, 0, 0, 0]
    diploma_getGood = [0, 0, 0, 0, 0]
    diploma_getGood_nonleader = [0, 0, 0, 0, 0] 
    diploma_getBad = [0, 0, 0, 0, 0] 

    counter = 0
    for (path, dirs, files) in os.walk(dirname):
        for filename in files:
            # skip cloud files
            if filename.startswith("csm-"):
                continue
            
            # get the first line of file
            headline = head(os.path.join(path, filename))
            # Continue if it's an empty file
            if not headline:
                continue
            # get the first timestamp from first line
            headtime = int(headline[0].strip()[:13])

            # get the last line of file
            tailine = tail(os.path.join(path, filename))
            tailtime = int(tailine[0].strip()[:13])

            # Continue if it's an old file 
            if tailtime < RUNTIMES[0]:
                continue

            # find middle of file for better calculations
            midtime = (headtime + tailtime)/2

            # Put the files into run buckets based on tail timestamp
            runNumber = runAssign(midtime) 
            #print
            #print runNumber, tailtime, path, filename
            
            ###################################################
            # get latency from the middle of the files
            isLatencyOk = False
            hasResultBad = False
            isGetValidR = True
            for line in open(os.path.join(path, filename)):
                # get the number of times clicked
                get_search = re.search("making GET photo PACKET to send to the leader. Requesting for region: (.*) ", line)
                #get_search = re.search("(.*):.*making GET photo PACKET to send to the leader", line)
                if get_search is not None:
                    #thistime = int(get_search.group(1))
                    #if thistime < RUNTIMES[runNumber] or thistime > RUNTIMES[runNumber+1]:
                    #    print "making get photo BADDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD"
                    #    continue
                    # only run 0 uses regions 4 and 5, but they would just time out 
                    isGetValidR = True
                    if runNumber > 0:
                        getRegion = int(get_search.group(1))
                        if getRegion < 4:
                            diploma_getNum[runNumber] += 1
                        else:
                            isGetValidR = False
                    else:
                        diploma_getNum[runNumber] += 1
                    

                if isGetValidR:
                    # Photo latencies are recorded before we know if there was a success
                    # so save the latencies temporarily in tmp_latency and 
                    # if there is a success, save it in the array
                    
                    # TODO: add leader/nonleader analysis
                    # Retrieve latency info
                    g = re.search("(.*)leader (.*) photo latency = (.*)", line)
                    #g = re.search("(.*): (.*)leader (.*) photo latency = (.*)", line)
                    if g is not None:
                        #thistime = int(g.group(1))
                        #if thistime < RUNTIMES[runNumber] or thistime > RUNTIMES[runNumber+1]:
                        #    print "photo latengy BADDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD"
                        #    continue
                        # reset flags
                        isLatencyOk = False
                        hasResultBad = False

                        #tmp_latency = int(g.group(4))
                        tmp_latency = int(g.group(3))
                        # there are times when strangely the start latency didn't get set
                        # For example, see 0/csm_dip-1335799100129.txt
                        # so it records latency as just the epoch time e.g. "1335799117233"
                        if tmp_latency < 1000000:
                            isLatencyOk = True

                    # t4. successfulness search only when isLatencyOk
                    if isLatencyOk:

                        if not hasResultBad:
                            # only search a bad when there has been no bad replies
                            # so that we don't double count bad replies from a single request
                            # see extra_notes/analysis_get_regions_diploma_1.txt for more info
                            sbad = re.search("one more getBad", line)
                            #sbad = re.search("(.*):.*one more getBad", line)
                            if sbad is not None:
                                #thistime = int(sbad.group(1))
                                #if thistime < RUNTIMES[runNumber] or thistime > RUNTIMES[runNumber+1]:
                                #    print "one more getBad BADDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD"
                                #    continue
                                hasResultBad = True
                                diploma_getBad[runNumber] += 1

                        sgood = re.search("one more getGood", line)
                        #sgood = re.search("(.*):.*one more getGood", line)
                        if sgood is not None:
                            #thistime = int(sgood.group(1))
                            #if thistime < RUNTIMES[runNumber] or thistime > RUNTIMES[runNumber+1]:
                            #    print "one more getGood BADDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD"
                            #    continue
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
        print "======= GETs for run %d ========" % iRun
        print "gets clicked:\t%d" % diploma_getNum[iRun]

        if diploma_getNum[iRun] > 0:
            print "successes:\t%d\t%d %%\tincluding existing regions without a photo" % (diploma_getGood[iRun], (diploma_getGood[iRun]*100/diploma_getNum[iRun]))
            print "fails:\t\t%d\tdue to null region, but still with reply" % diploma_getBad[iRun]
            print "~timed outs:\t%d\tcalculated indirectly by number-successes-fails" % (diploma_getNum[iRun]-diploma_getGood[iRun]-diploma_getBad[iRun])

            # only do latency when there are replies
            if (diploma_getGood[iRun] + diploma_getBad[iRun]) > 0:
                latencyPrints(diploma_get_latency[iRun])
            else:
                print "no latency calculations since no there are no replies"
        else: 
            print "no requests means no analysis"

if __name__ == "__main__":
    print "#############################################"
    print "###### Diploma Gets based on run number #####"
    print "#############################################"
    print "takes about 6 seconds to run for me"
    print
    dirname = "logs"
    dirWalk(dirname)
    print "============================="
    print "====== Diploma Notes ========"
    print "============================="
    printResults()
