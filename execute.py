#!/usr/bin/python

import sys
import json
import requests

from pathlib import Path
from requests.auth import HTTPBasicAuth
from optparse import OptionParser

if sys.version_info.major == 3:
    from urllib.parse import urlparse
else:
    from urlparse import urlparse

def executeGroovyScriptOnNode(scriptTextUrl, nodeName, user, token, script):

    groovy_script = "import hudson.util.RemotingDiagnostics\n" \
    "String result\n" \
    "Hudson.instance.slaves.find { agent ->\n" \
    "    agent.name == \"" + nodeName + "\"\n" \
    "}.with { agent ->\n" \
    "    result = RemotingDiagnostics.executeGroovy(\"\"\"" + script.strip() + "\"\"\", agent.channel)\n" \
    "}\n" \
    "println result"

    executeGroovyScript(scriptTextUrl, user, token, groovy_script, None)

def executeGroovyScript(scriptTextUrl, user, token, script, **args):

    for key in args:
        print(args[key])

    argString = ""
    try:
        viewPath = Path(args['view'])
        viewArgString = "viewPath = ["
        for p in viewPath.parts:
            viewArgString += '\'{}\','.format(p)
        viewArgString += "]"
        argString += viewArgString
    except KeyError:
        pass

    appendScript = 'run({})'.format(argString)
    print(appendScript)
    script += appendScript
    data = {'script': script}
    response = requests.post(
            scriptTextUrl, auth=HTTPBasicAuth(user, token),
            data=data)

    if response.status_code != 200:
        raise Exception('execute failed. code='+ str(response.status_code))
    print(response.text)

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

    parser.add_option("-a", "--args", type="string", default=None,
        action = "store", dest = "args",
        help = "JSON string arguments")

    (options, args) = parser.parse_args()  

    json_args = {}
    if options.args is not None:
        json_args = json.loads(options.args)
        print(json_args)

    # defult script
    script = 'println(Jenkins.instance.pluginManager.plugins)'
    if options.script_path is not None:
        with open(options.script_path, 'r') as file:
            script = file.read()

    if 'node' in json_args:
        executeGroovyScriptOnNode(
            urlparse(options.url + '/scriptText').geturl(),
            json_args['node'],
            options.username,
            options.token,
            script)
        sys.exit(0)

    executeGroovyScript(
        urlparse(options.url + '/scriptText').geturl(),
        options.username,
        options.token,
        script,
        **json_args)
