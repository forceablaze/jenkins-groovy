#!/usr/bin/python3

import json
import requests

from requests.auth import HTTPBasicAuth
from optparse import OptionParser
from urllib.parse import urlparse

import functools
print_flush = functools.partial(print, flush=True)

def executeGroovyScript(scriptTextUrl, user, token, script):
    data = {'script': script}
    response = requests.post(
            scriptTextUrl, auth=HTTPBasicAuth(user, token),
            data=data)

    if response.status_code != 200:
        raise Exception('execute failed. code='+ str(response.status_code))

    print_flush(response.text)

if __name__ == '__main__':

    parser = OptionParser()  

    parser.add_option("-U", "--url", type="string",
        action = "store", dest = "url",
        help = "Jenkins server url")

    parser.add_option("-u", "--username", type="string",
        action = "store", dest = "username",
        help = "Jenkins username")

    parser.add_option("-t", "--token", type="string",
        action = "store", dest = "token",
        help = "Jenkins API token")

    parser.add_option("-g", "--groovy", type="string", default=None,
        action = "store", dest = "script_path",
        help = "Groovy script path")

    (options, args) = parser.parse_args()  

    # defult script
    script = 'println(Jenkins.instance.pluginManager.plugins)'
    if options.script_path is not None:
        with open(options.script_path, 'r') as file:
            script = file.read()

    executeGroovyScript(
        urlparse(options.url + '/scriptText').geturl(),
        options.username,
        options.token,
        script)
        

