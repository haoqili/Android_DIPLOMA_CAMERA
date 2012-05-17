#!/usr/bin/env python

import datetime
import os
import re


cloud_requests_take_total = 0
cloud_requests_take_success = 0
cloud_requests_get_total = 0
cloud_requests_get_success = 0

diplo_requests_take_total = 0
diplo_requests_take_success = 0
diplo_requests_get_total = 0
diplo_requests_get_success = 0

for filename in os.listdir("."):
	if filename.startswith("diplo-"):
		for line in open(filename):
			if "takeNum" in line:
				last_count_line = line
		if 'last_count_line' in locals():
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
				
				diplo_requests_take_total = diplo_requests_take_total + takeNum
				diplo_requests_take_success = diplo_requests_take_success + takeGoodSave
				
				diplo_requests_get_total = diplo_requests_get_total + getNum
				diplo_requests_get_success = diplo_requests_get_success + getGood
				
				#print "diplo-take\t%5d\t%5d" % (diplo_requests_take_total, diplo_requests_take_success)
				#print "diplo-get\t%5d\t%5d" % (diplo_requests_get_total, diplo_requests_get_success)
				
				print "%s" % (last_count_line),
			
	elif filename.startswith("cloud-"):
		for line in open(filename):
			if "takeNum" in line:
				last_count_line = line
		if 'last_count_line' in locals():
			m = re.search('takeNum=(.*) takeCamGood=(.*) takeGoodSave=(.*) takeBad=(.*) getNum=(.*) getGood=(.*) getBad=(.*)', last_count_line)
			if m is not None:
				takeNum = int(m.group(1))
				takeCamGood = int(m.group(2))
				takeGoodSave = int(m.group(3))
				takeBad = int(m.group(4))
				getNum = int(m.group(5))
				getGood = int(m.group(6))
				getBad = int(m.group(7))
				
				cloud_requests_take_total = cloud_requests_take_total + takeNum
				cloud_requests_take_success = cloud_requests_take_success + takeGoodSave
				
				cloud_requests_get_total = cloud_requests_get_total + getNum
				cloud_requests_get_success = cloud_requests_get_success + getGood
				
				#print "cloud-take\t%5d\t%5d" % (cloud_requests_take_total, cloud_requests_take_success)
				#print "cloud-get\t%5d\t%5d" % (cloud_requests_get_total, cloud_requests_get_success)
				
				print "%s" % (last_count_line),

