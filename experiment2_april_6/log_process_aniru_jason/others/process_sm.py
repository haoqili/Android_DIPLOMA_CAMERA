import sys, re
from numpy import *

app_RequestsCount = 0
app_RequestsSuccess = 0
app_RequestsFailure = 0
app_RequestsSuccessLatencies = []

#for line in input_f.readlines():
for line in sys.stdin.readlines():
	# App
	#if "Reading" in line or "Releasing" in line or "Requesting" in line:
	#	app_RequestsCount = app_RequestsCount + 1
	if "succeeded, value=" in line or "succeeded, spots=" in line:
		app_RequestsSuccess = app_RequestsSuccess + 1
		app_RequestsSuccessLatencies.append(int(line.split(",latency=")[1]))
	if "failed" in line: app_RequestsFailure = app_RequestsFailure + 1

	

app_RequestsCount = app_RequestsSuccess + app_RequestsFailure
#app_RequestsFailure = app_RequestsCount - app_RequestsSuccess
print "--- App layer stats"
print "%d\trequests total" % (app_RequestsCount)
print "%d\trequests successful (%0.2f%%)" % (app_RequestsSuccess, 100*app_RequestsSuccess/float(app_RequestsCount))
print "%d\trequests failed" % (app_RequestsFailure)
print "%d ms\tmean latency" % (array(app_RequestsSuccessLatencies).mean())
print "%d ms\tstd-dev latency" % (array(app_RequestsSuccessLatencies).std())
