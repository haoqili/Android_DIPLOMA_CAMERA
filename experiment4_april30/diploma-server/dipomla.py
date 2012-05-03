# diploma
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
        print '\nupload() in ('+str(x)+', '+str(y)+') by id '+str(id)
        if request.method == 'POST':
                # require region to exist and only allow current leader to modify
                if not (x,y) in regions.keys():
                        print 'upload() region '+str(x)+', '+str(y)+') does not exist'
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
        print '\ntake_leadership() in ('+str(x)+', '+str(y)+') by id '+str(id)

        # create region entry if not found
        if not (x,y) in regions.keys():
                print 'this is a brand new region, create it'
                regions[(x,y)] = {'leader': None, 'csm_data': None, 'modified_time': -1L}

        # update current leader time / slow heartbeat? 1hr
        if regions[(x,y)]['leader'] is not None and regions[(x,y)]['leader'] == id:
                print 'heartbeat received'
                response = regions[(x,y)]
                response['status'] = CR_NOCSM
                regions[(x,y)]['modified_time'] = modified_time
                return jsonify(response)

        if regions[(x,y)]['leader'] is not None and not regions[(x,y)]['leader'] == id and regions[(x,y)]['modified_time'] + 100000 > modified_time: 
                print 'someone tried to take leadership of a region already being led'
                return jsonify({'status': CR_ERROR, 'leader': None, 'csm_data': None, 'modified_time': -1L})

        # is this a leadership takeover of a dead region?
        if regions[(x,y)]['leader'] is not None and not regions[(x,y)]['leader'] == id and regions[(x,y)]['modified_time'] + 100000 < modified_time: 
                print 'giving leadership of a dead region to a new leader last heard at: ' + str(regions[(x,y)]['modified_time']) 

        # record requesting node as the leader
        regions[(x,y)]['leader'] = id
        regions[(x,y)]['modified_time'] = modified_time
        print 'take_leadership() set region ('+str(x)+', '+str(y)+') with id = '+str(id)+' at '+ str(modified_time)

        # send CSM data back
        response = regions[(x,y)]
        response['status'] = CR_CSM if regions[(x,y)]['csm_data'] else CR_NOCSM
        return jsonify(response)


@app.route('/release/<int:x>/<int:y>/<int:id>/<int:modified_time>/')
def release_leadership(x, y, id, modified_time):
        """Save the CSM data from a leader and mark the region without leader."""
        print '\nrelease_leadership() in ('+str(x)+', '+str(y)+') by id '+str(id)

        # require region to exist and only allow current leader to modify
        if not (x,y) in regions.keys():
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
        app.run(host='0.0.0.0', port=8212)
