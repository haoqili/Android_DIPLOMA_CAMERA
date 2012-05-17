#!/usr/bin/env python

import datetime
import os
for filename in os.listdir("."):
	if filename.startswith("csm-"):
		timestamp = int(filename[4:14])
		filesize = os.path.getsize(filename)

		if filesize > 1000 and timestamp > 1333720800:
			diploma_or_cloud = os.system('grep NET_NAME ' + filename)

			date = datetime.datetime.fromtimestamp(timestamp).strftime('%Y-%m-%d-%H-%M-%S')
			with open(filename) as myfile:
				last_line = (list(myfile)[-1])
				last_timestamp = int(last_line[0:10])
				last_date =datetime.datetime.fromtimestamp(last_timestamp).strftime('%Y-%m-%d-%H-%M-%S')

			if diploma_or_cloud == 0:
				newname = "diplo-" + date + "-to-" + last_date + ".txt"
			else:
				newname = "cloud-" + date + "-to-" + last_date + ".txt"
			
			
			print "renaming " + newname
			os.rename(filename, newname)
		else:
			os.remove(filename)
			print "deleting " + filename
	else:
		os.remove(filename)
		print "deleting " + filename