/***********************************
 chefspec DSL

 Invokes chefspec for a cookbook

 example usage
 chefspec 'cookbook_dir'
 ************************************/

def call(body) {
    echo "Slack message stub method\n\t >>>> $body"
    sh '''#!/bin/bash
    eval "$(/opt/chefdk/bin/chef shell-init bash)"
    export LC_CTYPE=en_US.UTF-8
    echo "=========================================="
    echo "run chef build for cookbook: $body"
    echo "=========================================="
    cd $WORKSPACE/$cb
    gem install version
    berks install
    if [ $? -ne 0 ]; then
      echo "Berkshelf install Failed!"
      exit 2
    fi

    rake test
    if [ $? -ne 0 ]; then
      echo "chefspec failed!"
        exit 2
    fi
    echo "=========================================="
    echo "completed cookbook build $body"
    echo "=========================================="
    '''

}
