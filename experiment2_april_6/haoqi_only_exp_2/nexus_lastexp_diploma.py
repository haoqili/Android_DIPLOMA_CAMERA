import os
import re

uplat_leader = []
uplat_nonleader = []

getlat_leader = []
getlat_nonleader = []

diplo_requests_take_total = 0 
diplo_requests_take_success = 0 
diplo_requests_take_bad = 0 
diplo_requests_take_tout = 0

diplo_requests_get_total = 0 
diplo_requests_get_success = 0
diplo_requests_get_bad = 0
diplo_requests_get_tout = 0

def dirWalk(dirname):
    files = os.listdir(dirname)
    tmp = ""

    global diplo_requests_take_total
    global diplo_requests_take_success
    global diplo_requests_take_bad 
    global diplo_requests_take_tout 

    global diplo_requests_get_total 
    global diplo_requests_get_success
    global diplo_requests_get_bad
    global diplo_requests_get_tout

    for filename in files:
        last_count_line = ""
        for line in open(os.path.join(dirname, filename)):
            n = re.search("(.*) upload new photo latency = (.*)", line)
            if n is not None:
                if n.group(1) == "leader":
                    uplat_leader.append(int(n.group(2)))
                else: # == "nonleader"
                    uplat_nonleader.append(int(n.group(2)))

            g = re.search("(.*) download remote photo latency = (.*)", line)
            if g is not None:
                if g.group(1) == "leader":
                    getlat_leader.append(int(g.group(2)))
                else: # == "nonleader"
                    getlat_nonleader.append(int(g.group(2)))


            if "takeNum" in line:
                last_count_line = line

        if 'last_count_line' != "":
            m = re.search('takeNum=(.*) takeCamGood=(.*) takeGoodSave=(.*) takeBad=(.*) takeTimedout=(.*) getNum=(.*) getGood=(.*) getBad=(.*) getTimedout=(.*)', last_count_line)
            if m is not None:
                takeNum = int(m.group(1))
                takeCamGood = int(m.group(2))
                takeGoodSave = int(m.group(3))
                takeBad = int(m.group(4))
                takeTimedout = int(m.group(5))
                getNum = int(m.group(6))
                getGood = int(m.group(7))
                getBad = int(m.group(8))
                getTimedout = int(m.group(9))
                    
                diplo_requests_take_total += takeNum
                diplo_requests_take_success += takeGoodSave
                diplo_requests_take_bad += takeBad
                diplo_requests_take_tout += takeTimedout

                diplo_requests_get_total += getNum
                diplo_requests_get_success += getGood
                diplo_requests_get_bad += getBad
                diplo_requests_get_tout += getTimedout

if __name__ == "__main__":
    dirname = "last_exp/diploma"
    dirWalk(dirname)
    print "Nexus last experiment Diploma"
    print "---------------------------"

    print
    print "TAKE a picture"
    print "count take requests: %d" % diplo_requests_take_total
    print "count take successes: %d" % diplo_requests_take_success
    print "count take bad: %d" % diplo_requests_take_bad
    print "count take timedout: %d" % diplo_requests_take_tout

    print "Take latencies ----"
    print "Take leader requests %d" % len(uplat_leader)
    if len(uplat_leader) > 0:
        print "Take leader mean: %d" % (sum(uplat_leader)/len(uplat_leader))
    print "Take nonleader requests %d" % len(uplat_nonleader)
    print "Take nonleader mean: %d" % (sum(uplat_nonleader)/len(uplat_nonleader))
    uplat = uplat_leader[:]
    uplat.extend(uplat_nonleader)
    print "Take total with latencies: %d" % len(uplat)
    print "Take mean: %d" % (sum(uplat)/len(uplat))
    uplat_s = sorted(uplat)
    print "Take median: %d" % uplat_s[len(uplat)/2]

    print
    print "GET a picture"
    print "count get requests: %d" % diplo_requests_get_total
    print "count get successes: %d" % diplo_requests_get_success
    print "count get bad: %d" % diplo_requests_get_bad
    print "count get timedout: %d" % diplo_requests_get_tout

    print "get latencies ----"
    print "get leader requests %d" % len(getlat_leader)
    if len(getlat_leader) > 0:
        print "get leader mean: %d" % (sum(getlat_leader)/len(getlat_leader))
    print "get nonleader requests %d" % len(getlat_nonleader)
    print "get nonleader mean: %d" % (sum(getlat_nonleader)/len(getlat_nonleader))
    getlat = getlat_leader[:]
    getlat.extend(getlat_nonleader)
    print "get total with latencies: %d" % len(getlat)
    print "get mean: %d" % (sum(getlat)/len(getlat))
    getlat_s = sorted(getlat)
    print "get median: %d" % getlat_s[len(getlat)/2]

    print "TOTAL"
    total = uplat[:]
    total.extend(getlat)
    print "Total requests: %d" % len(total)
    print "Total mean: %d" % (sum(total)/len(total))
    print "Total median: %d" % total[len(total)/2]
