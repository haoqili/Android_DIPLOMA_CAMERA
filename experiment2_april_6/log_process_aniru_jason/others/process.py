# things to calc:
# UserClient request completion %
# CSM request completion %
# 3G request completion %
#
# UserClient successful requests latency avg? dist?
# CSM successful requests latency avg? dist?
# 3g successful requests latency avg? dist?
#
# UserClient # wifi packets & sizes
# CSM # wifi packets & sizes
# VN # wifi packets & sizes
#
# UserClient # 3g packets & sizes
# CSM # 3g packets & sizes
# VN # 3g packets & sizes
# 
import sys, re
from numpy import *

#input_f = open(sys.argv[1], 'r')
# strip extraneous information if it's an adb-style log
#adb_tags = ['VNC:Cloud', 'Mux', 'NetworkThread', 'StatusActivity', 'UserClient', 'VNCDaemon']
#adb = True
# TODO


app_RequestsCount = 0
app_RequestsSuccess = 0
app_RequestsFailure = 0
app_RequestsSuccessLatencies = []

app_RequestsCountRead = 0
app_RequestsSuccessRead = 0
app_RequestsFailureRead = 0
app_RequestsSuccessLatenciesRead = []

app_RequestsCountWrite = 0
app_RequestsSuccessWrite = 0
app_RequestsFailureWrite = 0
app_RequestsSuccessLatenciesWrite = []


csm_RequestsCount = 0
csm_RequestsSuccess = 0
csm_RequestsFailure = 0
csm_RequestsCacheHit = 0

csm_RequestsCount0 = 0
csm_RequestsSuccess0 = 0
csm_RequestsFailure0 = 0
csm_RequestsCacheHit0 = 0

csm_RequestsCount1 = 0
csm_RequestsSuccess1 = 0
csm_RequestsFailure1 = 0
csm_RequestsCacheHit1 = 0

csm_RequestsCount2 = 0
csm_RequestsSuccess2 = 0
csm_RequestsFailure2 = 0
csm_RequestsCacheHit2 = 0

csm_RequestsCount3 = 0
csm_RequestsSuccess3 = 0
csm_RequestsFailure3 = 0
csm_RequestsCacheHit3 = 0

vnc_leaderElectAttempt = 0
vnc_leaderRegionEmpty = 0
vnc_leaderRegionNotEmpty = 0
vnc_leaderConfirmAck = 0


cloud_accessCount = 0
cloud_leadershipAccept = 0
cloud_leadershipReject = 0
cloud_leadershipRelease = 0
cloud_leadershipUpload = 0
cloud_leadershipUploadBytes = 0


# packet counting
pkt_totalRx = 0;
pkt_totalTx = 0;
pkt_totalRxSize = 0;
pkt_totalTxSize = 0;

# matching up packet sizes with logical actions
pkt_lastRxSize = 0;
pkt_lastTxSize = 0;

# UserClient
#for line in input_f.readlines():
for line in sys.stdin.readlines():
	#timestamp, line = line.split(':', 1)
	
	# packets
	if "Sending UDP payload: " in line:
		pkt_lastTxSize = int(line.split("Sending UDP payload: ")[1])
		pkt_totalTxSize = pkt_totalTxSize + pkt_lastTxSize
		pkt_totalTx = pkt_totalTx + 1
		continue
	
	elif "Received UDP payload: " in line:
		pkt_lastRxSize = int(line.split("Received UDP payload: ")[1])
		pkt_totalRxSize = pkt_totalRxSize + pkt_lastRxSize
		pkt_totalRx = pkt_totalRx + 1
		continue
	
	
	elif "cloud rejected leadership request" in line:
		cloud_accessCount = cloud_accessCount + 1
		cloud_leadershipReject = cloud_leadershipReject + 1
	
	elif "cloud accepted leadership request" in line:
		cloud_accessCount = cloud_accessCount + 1
		cloud_leadershipAccept = cloud_leadershipAccept + 1
	
	elif "released leadership to cloud" in line:
		cloud_accessCount = cloud_accessCount + 1
		cloud_leadershipRelease = cloud_leadershipRelease + 1
	
	elif "uploaded state to cloud" in line:
		cloud_accessCount = cloud_accessCount + 1
		cloud_leadershipRelease = cloud_leadershipRelease + 1
		cloud_leadershipUpload = cloud_leadershipUpload + 1
		size = int(line.split("json ")[1].split(" bytes")[0])
		cloud_leadershipUploadBytes = cloud_leadershipUploadBytes + size
		
	
	# App
	elif "spot in" in line or "ticket in" in line:
		app_RequestsCount = app_RequestsCount + 1
		if "Reading" in line:
			app_RequestsCountRead = app_RequestsCountRead + 1
		elif "Releasing" in line:
			app_RequestsCountWrite = app_RequestsCountWrite + 1
		elif "Requesting" in line:
			app_RequestsCountWrite = app_RequestsCountWrite + 1
		
	elif ("UserClient" in line or "Parking" in line) and "succeeded" in line:
		app_RequestsSuccess = app_RequestsSuccess + 1
		latency = int(line.split(",latency=")[1])
		app_RequestsSuccessLatencies.append(latency)
		if "decrement" in line or "Parking request" in line:
			app_RequestsSuccessWrite = app_RequestsSuccessWrite + 1
			app_RequestsSuccessLatenciesWrite.append(latency)
		elif "increment" in line or "Parking release" in line:
			app_RequestsSuccessWrite = app_RequestsSuccessWrite + 1
			app_RequestsSuccessLatenciesWrite.append(latency)
		elif "read on" in line or "Parking read" in line:
			app_RequestsSuccessRead = app_RequestsSuccessRead + 1
			app_RequestsSuccessLatenciesRead.append(latency)
		
	
	# CSM
	elif "Local cache hit on read-only" in line:
		csm_RequestsCacheHit = csm_RequestsCacheHit + 1
	elif "Sending PROC_REQUEST" in line:
		csm_RequestsCount = csm_RequestsCount + 1
		if "Sending PROC_REQUEST 0" in line:
			csm_RequestsCount0 = csm_RequestsCount0 + 1
		elif "Sending PROC_REQUEST 1" in line:
			csm_RequestsCount1 = csm_RequestsCount1 + 1
		elif "Sending PROC_REQUEST 2" in line:
			csm_RequestsCount2 = csm_RequestsCount2 + 1
		elif "Sending PROC_REQUEST 3" in line:
			csm_RequestsCount3 = csm_RequestsCount3 + 1
	elif "Received PROC_REPLY" in line:
		csm_RequestsSuccess = csm_RequestsSuccess + 1
		if "Received PROC_REPLY 0" in line:
			csm_RequestsSuccess0 = csm_RequestsSuccess0 + 1
		elif "Received PROC_REPLY 1" in line:
			csm_RequestsSuccess1 = csm_RequestsSuccess1 + 1
		elif "Received PROC_REPLY 2" in line:
			csm_RequestsSuccess2 = csm_RequestsSuccess2 + 1
		elif "Received PROC_REPLY 3" in line:
			csm_RequestsSuccess3 = csm_RequestsSuccess3 + 1
	# account for Received PROC_REPLY showing up after failure PROC_REPLY
	elif "Request timed out, send failure PROC_REPLY" in line:
		csm_RequestsSuccess = csm_RequestsSuccess - 1
		if "Request timed out, send failure PROC_REPLY 0" in line:
			csm_RequestsSuccess0 = csm_RequestsSuccess0 - 1
		elif "Request timed out, send failure PROC_REPLY 1" in line:
			csm_RequestsSuccess1 = csm_RequestsSuccess1 - 1
		elif "Request timed out, send failure PROC_REPLY 2" in line:
			csm_RequestsSuccess2 = csm_RequestsSuccess2 - 1
		elif "Request timed out, send failure PROC_REPLY 3" in line:
			csm_RequestsSuccess3 = csm_RequestsSuccess3 - 1

	
	# VNC
	elif "broadcasting LEADER_ELECT" in line: vnc_leaderElectAttempt = vnc_leaderElectAttempt + 1
	elif "old region empty" in line: vnc_leaderRegionEmpty = vnc_leaderRegionEmpty + 1
	elif "sending LEADER_CONFIRM" in line: vnc_leaderRegionNotEmpty = vnc_leaderRegionNotEmpty + 1
	elif "recv LEADER_CONFIRM_ACK" in line: vnc_leaderConfirmAck = vnc_leaderConfirmAck + 1
	

app_RequestsFailure = app_RequestsCount - app_RequestsSuccess
print "--- App layer stats - TOTAL"
print "%d\trequests total" % (app_RequestsCount)
print "%d\trequests successful (%0.2f%%)" % (app_RequestsSuccess, 100*app_RequestsSuccess/float(app_RequestsCount))
print "%d\trequests failed" % (app_RequestsFailure)
print "%d ms\tmean latency" % (array(app_RequestsSuccessLatencies).mean())
print "%d ms\tstd-dev latency" % (array(app_RequestsSuccessLatencies).std())

#app_RequestsFailureRead = app_RequestsCountRead - app_RequestsSuccessRead
print "--- App layer stats - READ"
print "%d\tread requests total" % (app_RequestsCountRead)
print "%d\tread requests successful (%0.2f%%)" % (app_RequestsSuccessRead, 100*app_RequestsSuccessRead/float(app_RequestsCountRead))
#print "%d\tread requests failed" % (app_RequestsFailureRead)
print "%d ms\tread mean latency" % (array(app_RequestsSuccessLatenciesRead).mean())
print "%d ms\tread std-dev latency" % (array(app_RequestsSuccessLatenciesRead).std())

#app_RequestsFailureWrite = app_RequestsCountWrite - app_RequestsSuccessWrite
print "--- App layer stats - WRITE"
print "%d\twrite requests total" % (app_RequestsCountWrite)
print "%d\twrite requests successful (%0.2f%%)" % (app_RequestsSuccessWrite, 100*app_RequestsSuccessWrite/float(app_RequestsCountWrite))
#print "%d\twrite requests failed" % (app_RequestsFailureWrite)
print "%d ms\twrite mean latency" % (array(app_RequestsSuccessLatenciesWrite).mean())
print "%d ms\twrite std-dev latency" % (array(app_RequestsSuccessLatenciesWrite).std())

#csm_RequestsFailure = csm_RequestsCount - csm_RequestsSuccess
print "--- CSM layer stats - TOTAL"
print "%d\trequests total" % (csm_RequestsCount)
print "%d\trequests successful (%0.2f%%) (%d read cache hits)" % (csm_RequestsSuccess, 100*csm_RequestsSuccess/float(csm_RequestsCount), csm_RequestsCacheHit)
#print "%d\trequests failed" % (csm_RequestsFailure)

print "--- CSM layer stats - READ"
print "%d\trequests total" % (csm_RequestsCount3)
print "%d\trequests successful (%0.2f%%) (%d read cache hits)" % (csm_RequestsSuccess3, 100*csm_RequestsSuccess3/float(csm_RequestsCount3), csm_RequestsCacheHit)


csm_RequestsCountWrite = csm_RequestsCount0 + csm_RequestsCount1 + csm_RequestsCount2
csm_RequestsSuccessWrite = csm_RequestsSuccess0 + csm_RequestsSuccess1 + csm_RequestsSuccess2
print "--- CSM layer stats - WRITE"
print "%d\trequests total" % (csm_RequestsCountWrite)
print "%d\trequests successful (%0.2f%%)" % (csm_RequestsSuccessWrite, 100*csm_RequestsSuccessWrite/float(csm_RequestsCountWrite))



print "--- VirtualNode layer stats"
print "%d\thand-off attempts" % (vnc_leaderElectAttempt)
print "%d\thand-off attempts succeed" % (vnc_leaderConfirmAck)
print "%d\thand-off attempts fail due to apparently empty region" % (vnc_leaderRegionEmpty)
print "%d\thand-off attempts fail at last handshake" % (vnc_leaderElectAttempt - vnc_leaderConfirmAck - vnc_leaderRegionEmpty)

print "--- Cloud access stats"
print "%d\ttotal accesses" % (cloud_accessCount)
print "%d\tleadership rejects" % (cloud_leadershipReject)
print "%d\tleadership accepts" % (cloud_leadershipAccept)
print "%d\tleadership releases" % (cloud_leadershipRelease)
print "%d\tCSM uploads by leader" % (cloud_leadershipUpload)
print "%d\tbytes of CSM state uploaded" % (cloud_leadershipUploadBytes)

print "--- Packet stats"
print "%d\tpackets received" % (pkt_totalRx)
print "%d\tpackets sent" % (pkt_totalTx)
print "%d\tbytes received" % (pkt_totalRxSize)
print "%d\tbytes sent" % (pkt_totalTxSize)
print "%d\tavg packet size received" % (pkt_totalRxSize / pkt_totalRx)
print "%d\tavg packet size sent" % (pkt_totalTxSize / pkt_totalTx)

print "--- Total runtime across 10 phones"
totalTime = sum(app_RequestsSuccessLatencies) + 5000*app_RequestsFailure + 2000*app_RequestsCount
print "%d s total" % (totalTime / 1000)
print "%d s avg per phone" % (totalTime / 1000 / 10)
