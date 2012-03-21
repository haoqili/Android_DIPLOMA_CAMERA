from __future__ import with_statement
import sqlite3
from contextlib import closing
from flask import Flask, redirect, request, g, url_for, abort, render_template, jsonify


#########################
# configuration
#########################
DEBUG = True
SECRET_KEY = 'devkey'

app = Flask(__name__)
app.config.from_object(__name__)
app.config.from_envvar('FLASKR_SETTINGS', silent=True)


#########################
# app - web side
#########################

# regions
regions = dict()

CR_ERROR = 13
CR_OKAY = 12
CR_NOCSM = 11
CR_CSM = 10

@app.route('/')
def show_regions():
	return render_template('show_regions.html', regions=regions)


@app.route('/reset/')
def reset():
	# clear region and csm data
	global regions
	regions = dict()
	return redirect(url_for('show_regions'))

#########################
# app - phone side
#########################
@app.route('/upload/<int:x>/<int:y>/<int:id>/<int:modified_time>/', methods=['GET', 'POST'])
def upload(x, y, id, modified_time):
	if request.method == 'POST':
		# require region to exist and only allow current leader to modify
		if not (x,y) in regions.keys():
			print 'region does not exist'
			return jsonify({'status': CR_ERROR})
		if not regions[(x,y)]['leader'] == id:
			print 'leader id mismatch: got %d, expected %d' % (regions[(x,y)]['leader'], id)
			return jsonify({'status': CR_ERROR})

		# save CSM data and mark region as without leader
		regions[(x,y)]['csm_data'] = request.form['csm_data']
		
		return jsonify({'status': CR_OKAY})
	else:
		print 'request method not POST'
		return jsonify({'status': CR_ERROR})
	
	
@app.route('/take/<int:x>/<int:y>/<int:id>/<int:modified_time>/')
def take_leadership(x, y, id, modified_time):
	"""Respond to a region leadership request from a node"""
	
	# create region entry if not found
	if not (x,y) in regions.keys():
		regions[(x,y)] = {'leader': None, 'csm_data': None, 'modified_time': -1L}
	
	# update current leader time / slow heartbeat? 1hr
	if regions[(x,y)]['leader'] is not None and regions[(x,y)]['leader'] == id:
		response = regions[(x,y)]
		response['status'] = CR_NOCSM
		return jsonify(response)
	
	# currently being led, by a different node, who checked in < 1 hr ago?
	if regions[(x,y)]['leader'] is not None and not regions[(x,y)]['leader'] == id and regions[(x,y)]['modified_time'] + 3600000 > modified_time: 
		return jsonify({'status': CR_ERROR, 'leader': None, 'csm_data': None, 'modified_time': -1L})
	
	# record requesting node as the leader
	regions[(x,y)]['leader'] = id
	regions[(x,y)]['modified_time'] = modified_time
	
	# send CSM data back
	response = regions[(x,y)]
	response['status'] = CR_CSM if regions[(x,y)]['csm_data'] else CR_NOCSM
	return jsonify(response)
	

@app.route('/release/<int:x>/<int:y>/<int:id>/<int:modified_time>/')
def release_leadership(x, y, id, modified_time):
	"""Save the CSM data from a leader and mark the region without leader."""

	# require region to exist and only allow current leader to modify
	if not (x,y) in regions.keys():
		print 'region does not exist'
		return jsonify({'status': CR_ERROR})
	if not regions[(x,y)]['leader'] == id:
		print 'leader id mismatch: got %d, expected %d' % (regions[(x,y)]['leader'], id)
		return jsonify({'status': CR_ERROR})
	
	# mark region as without leader
	regions[(x,y)]['leader'] = None	
	regions[(x,y)]['modified_time'] = modified_time
	
	return jsonify({'status': CR_OKAY})
	

#########################
# run
#########################
if __name__ == '__main__':
	app.run(host='0.0.0.0', port=5212)
