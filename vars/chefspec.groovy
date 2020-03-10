/***********************************
 chefspec DSL

 Invokes chefspec for a cookbook

 example usage
 chefspec 'cookbook_dir'
 ************************************/

def call(body) {
    withEnv(["COOKBOOK=${body}"]) {
      sh '''#!/bin/bash
      eval "$(/opt/chefdk/bin/chef shell-init bash)"
      export LC_CTYPE=en_US.UTF-8
      echo "=========================================="
      echo "run chef build for cookbook: ${COOKBOOK}"
      echo "=========================================="
      cd $WORKSPACE/$COOKBOOK
      gem install version
      gem install rspec_junit_formatter
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
      echo "completed cookbook build ${COOKBOOK}"
      echo "=========================================="
      cd $WORKSPACE
      '''
    }
}
