/***********************************
 cookbookPublish DSL

 Invokes berks package and stash cookbook in the pipeline

 example usage
 cookbookPublish 'cookbook_dir'
 ************************************/

def call(body) {
  withEnv(["COOKBOOK=${body}"]) {
    sh '''#!/bin/bash
    eval "$(/opt/chefdk/bin/chef shell-init bash)"
    export LC_CTYPE=en_US.UTF-8
    echo "==================================================="
    echo "Publishing cookbook: ${COOKBOOK}"
    echo "==================================================="
    cd $WORKSPACE/$COOKBOOK
    berks package ./../cookbooks.tar.gz
    echo "==================================================="
    echo "completed publish for cookbook: ${COOKBOOK}"
    echo "==================================================="
    cd $WORKSPACE
    '''
    stash(name: 'cookbook', includes: '**/cookbooks.tar.gz')
  }
}
