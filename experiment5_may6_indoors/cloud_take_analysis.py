# based on diploma_get_analysis.py
import os
import re
import math

# These are the timestamps of the first requests to the server
#       ordered by their run number
# The last value is the end of the last request of the last run
RUNTIMES = [1336323068975, 
            1336326169221,
            1336327226416]

# the ith element is about run i (the ith time cloud server restarts)

cloud_take_latency = [[], [], [], [], []]
cloud_takeNum = [0, 0, 0, 0, 0]
cloud_takeGood = [0, 0, 0, 0, 0]
cloud_takeGood_nonleader = [0, 0, 0, 0, 0]
cloud_takeBad = [0, 0, 0, 0, 0] 

timeoutPeriod = 6000

# different algorithm than experiment 4
def runAssign(time):
    if time > RUNTIMES[1]:
        return 1
    else:
        return 0

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

    global cloud_take_latency
    global cloud_takeNum 
    global cloud_takeGood
    global cloud_takeGood_nonleader
    global cloud_takeBad

    cloud_take_latency = [[], [], [], [], []]
    cloud_takeNum = [0, 0, 0, 0, 0]
    cloud_takeGood = [0, 0, 0, 0, 0]
    cloud_takeGood_nonleader = [0, 0, 0, 0, 0] 
    cloud_takeBad = [0, 0, 0, 0, 0] 

    counter = 0
    for (path, dirs, files) in os.walk(dirname):
        for filename in files:
            # skip diploma files
            if filename.startswith("csm_dip0506-"):
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

            # Put the files into run buckets based on tail timestamp
            runNumber = runAssign(tailtime) 
            #print
            #print runNumber, tailtime, path, filename
            
            ###################################################
            # get latency from the middle of the files
            isLatencyOk = False
            hasResultBad = False
            for line in open(os.path.join(path, filename)):
                # get the number of times clicked
                take_search = re.search("Clicked take picture button", line)
                if take_search is not None:
                    cloud_takeNum[runNumber] += 1

                # Photo latencies are recorded before we know if there was a success
                # so save the latencies temporarily in tmp_latency and 
                # if there is a success, save it in the array
                
                # TODO: add leader/nonleader analysis
                # Retrieve latency info
                g = re.search("CameraCloud Execute HTTP Upload latency: (.*)ms", line)
                if g is not None:
                    # reset flags
                    isLatencyOk = False
                    hasResultBad = False

                    tmp_latency = int(g.group(1))
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
                        # see extra_notes/analysis_take_regions_diploma_1.txt for more info
                        sbad = re.search("one more takeBad", line)
                        if sbad is not None:
                            hasResultBad = True
                            cloud_takeBad[runNumber] += 1

                    sgood = re.search("one more takeGoodSave", line)
                    if sgood is not None:
                        cloud_takeGood[runNumber] += 1
                        cloud_take_latency[runNumber].append(tmp_latency)

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
        print "======= Takes for run %d ========" % iRun
        print "takes clicked:\t%d" % cloud_takeNum[iRun]

        if cloud_takeNum[iRun] > 0:
            print "successes:\t%d\t%d %%" % (cloud_takeGood[iRun], (cloud_takeGood[iRun]*100/cloud_takeNum[iRun]))
            print "fails:\t\t%d" % cloud_takeBad[iRun]
            print "~timed outs:\t%d\tcalculated indirectly by number-successes-fails" % (cloud_takeNum[iRun]-cloud_takeGood[iRun]-cloud_takeBad[iRun])

            # only do latency when there are replies
            if (cloud_takeGood[iRun] + cloud_takeBad[iRun]) > 0:
                latencyPrints(cloud_take_latency[iRun])
            else:
                print "no latency calculations since no there are no replies"
        else: 
            print "no requests means no analysis"

if __name__ == "__main__":
    dirname = "exp5_logs"
    dirWalk(dirname)
    print "about 3 seconds to run"
    print
    print "#############################################"
    print "###### Cloud Takes based on run number ######"
    print "#############################################"
    printResults()
