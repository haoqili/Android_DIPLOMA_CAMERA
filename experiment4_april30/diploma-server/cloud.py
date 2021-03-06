# cloud
from __future__ import with_statement
from contextlib import closing
from flask import Flask, redirect, request, g, url_for, abort, render_template, jsonify


#########################
# configuration
#########################
DEBUG = True

app = Flask(__name__)
app.config.from_object(__name__)
#app.config.from_envvar('FLASKR_SETTINGS', silent=True)

#########################
# app - web side
#########################

# savedata_here
regions = dict()

CR_ERROR = 13
CR_OKAY = 12
CR_NO_PHOTO = 20

@app.route('/')
def show_regions():
    return "hello world"


#########################
# app - phone side
#########################
@app.route('/101/<int:x>/<int:y>/', methods=['GET', 'POST'])
def client_upload_photo(x, y):
    """ save this photo to region (x, y)'s photo array """
    print "process CLIENT_UPLOAD_PHOTO"
    if request.method == 'POST':
        print "request from "+ str(x) + ", " + str(y)
        # require region to exist and only allow current leader to modify
        if not (x,y) in regions.keys():
            print "make new region"
            # make new key (x, y) and set it to photo_bytes array
            regions[(x,y)] = [request.json["photo_bytes"]]
            print "Save photo successful :D:D:D:D"
            return jsonify({'status': CR_OKAY})
        else:
            print "got new photo, replace the old photo"
            # TODO: if ever save multiple photos, change back
            # append to current array
            #regions[(x,y)].append(request.json["photo_bytes"])
            # set to first of array (ONLY SAVING ONE)
            regions[(x,y)][0] = request.json["photo_bytes"]
            print "Save photo successful :D:D:D:D"
            return jsonify({'status': CR_OKAY})
    else:
        print "request method not POST :(:(:(:("
        return jsonify({'status': CR_ERROR})
    
    
@app.route('/102/<int:x>/<int:y>/', methods=['GET', 'POST'])
def client_download_photo(x, y):
    """ send region (x, y)'s newest photo as a reply """
    print "process CLIENT_DOWNLOAD_PHOTO"
    if request.method == 'POST':
        print 'request about '+ str(x) + ", " + str(y)
        # require region to exist and only allow current leader to modify
        if not (x,y) in regions.keys():
            print "Requested region NOT in regions dictionary"
            return jsonify({'status': CR_NO_PHOTO})
        else:
            print "Requested region IN dictinary"
            # retrieve last element of  photo_bytes array, 
            # i.e. newest photo of this region
            print "about to return 'photo_bytes' with the latest photo "
            return jsonify({'photo_bytes': regions[(x,y)][-1]})
    else:
        print 'request method not POST'
        return jsonify({'status': CR_ERROR})


#########################
# run
#########################
if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8213)
