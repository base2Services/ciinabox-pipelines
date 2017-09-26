/***********************************
 kitchenDocker DSL

 Invokes run kitchen test using docker

 example usage
 kitchenDocker 'cookbook_dir'
 ************************************/

def call(body) {
  withEnv(["COOKBOOK=${body}"]) {
    sh '''#!/bin/bash
    eval "$(/opt/chefdk/bin/chef shell-init bash)"
    export LC_CTYPE=en_US.UTF-8
    echo "==================================================="
    echo "run kitchen test (Docker) for cookbook: ${COOKBOOK}"
    echo "==================================================="
    cd $WORKSPACE/$COOKBOOK
    if [ -r ".kitchen.docker.yml" ]; then
      export KITCHEN_YAML=.kitchen.docker.yml
      kitchen destroy default
      #kitchen test default
      if [ $? -ne 0 ]; then
        echo "kitchen test failed!"
          exit 2
      fi
    else
      echo "no .kitchen.yml defined for cookbook: ${COOKBOOK}"
    fi
    echo "========================================================="
    echo "completed kitchen test (Docker) for cookbook: ${COOKBOOK}"
    echo "========================================================="
    cd $WORKSPACE
    '''
  }
}
