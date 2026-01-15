import sys
import os
import shutil
import subprocess
import time
import glob
import requests
from concurrent.futures import ThreadPoolExecutor, as_completed
import multiprocessing

WORKERS=32

# workaround to disable proxy which prevents us from reaching localhost
# https://stackoverflow.com/questions/28521535/requests-how-to-disable-bypass-proxy
session = requests.Session()
session.trust_env = False

os.environ['NO_PROXY'] = 'localhost'

def main():
    if len(sys.argv) != 4:
        print("Usage: {} <solr_path> <tmp_port_to_use> <mem>".format(sys.argv[0]))
        sys.exit(1)

    solr_path = sys.argv[1]
    port = sys.argv[2]
    mem = sys.argv[3]

    print(f"solr_import.dockerpy: port {port}, mem {mem}")

    os.environ['SOLR_ENABLE_REMOTE_STREAMING'] = 'true'
    os.environ['SOLR_SECURITY_MANAGER_ENABLED'] = 'false'
    os.environ['JAVA_TOOL_OPTIONS'] = '-Djava.net.useSystemProxies=false'

    os.makedirs(solr_path + '/solr/logs', exist_ok=True)
    os.environ['SOLR_LOGS_DIR'] = solr_path + '/solr/logs'

    cmd = [solr_path + '/bin/solr', 'start', '-m', mem, '-p', port, '-noprompt', '-force', '--solr-home', solr_path]
    print(' '.join(cmd))
    subprocess.run(cmd)

    time.sleep(30)

    #subprocess.run(['wait-for-solr.sh', '--solr-url', f"http://127.0.0.1:{port}/solr/{core}/select?q=*:*"])

    time.sleep(30)

    filenames = glob.glob("**/*.jsonl") 
        
    print("ls")
    os.system("ls -Lhl")
    print(f"Found filenames: {','.join(filenames)}")

    with ThreadPoolExecutor(max_workers=WORKERS) as executor:
        futures = [executor.submit(upload_file, port, filename) for filename in filenames]
    
    time.sleep(5)
    # response = session.get(f"http://127.0.0.1:{port}/solr/ols4_entities/update",
    #                         params={'commit': 'true', 'optimize': 'true', 'maxSegments': '1'})
    response = session.get(f"http://127.0.0.1:{port}/solr/ols4_entities/update",
                            params={'commit': 'true'})
    print(response.text)

    time.sleep(5)
    response = session.get(f"http://127.0.0.1:{port}/solr/ols4_autocomplete/update",
                            params={'commit': 'true'})
    print(response.text)

    os.environ['SOLR_STOP_WAIT'] = '500'
    subprocess.run([solr_path + '/bin/solr', 'stop', '-p', port])

def upload_file(port, filename):
    if 'autocomplete' in filename:
        core = 'ols4_autocomplete'
    else:
        core = 'ols4_entities'
    print(f"Uploading {core.split('_')[1]} file: {filename}")
    response = session.get(f"http://127.0.0.1:{port}/solr/{core}/update/json/docs",
                            params={
                                'stream.file': os.path.realpath(filename),
                                'stream.contentType': 'application/json;charset=utf-8',
                                'commit': 'true'
                            })
    r = response.text.replace("\n", "")
    print(f"Uploaded {filename}: {r}")

if __name__ == "__main__":
    main()
