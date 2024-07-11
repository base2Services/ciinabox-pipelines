package com.base2.ciinabox

import groovy.json.JsonOutput

class PackerTemplateBuilder implements Serializable {

  String id
  String date
  String type
  Map builder = [:]
  ArrayList provisioners = []

  PackerTemplateBuilder(String name, String type = 'linux') {
    this.id = 'bake-' + UUID.randomUUID().toString()
    this.type = type
    def now = new Date()
    this.date = now.format("yyyy-MM-dd't'HH-mm-ss.SSS'z'", TimeZone.getTimeZone('UTC'))
    def amiName = "${name}-${this.date}"

    this.builder.type = 'amazon-ebs'
    this.builder.ami_name = amiName
    this.builder.run_tags = [
      "Status": "Baking",
      "Name": "Packer ${amiName}",
      "BakeId": this.id,
      "Role": name
    ]
    this.builder.tags = [
      "Status": "Baked",
      "Name": amiName,
      "BakeId": this.id,
      "Role": name
    ]
  }

  public void addAmiTags(Map tags) {
    this.builder.tags += tags 
  }

  public void addWindowsUpdate(){
    if (this.type.startsWith('windows')) {
      this.provisioners.push([
          type: 'windows-update'
      ])
    }
  }

  public void addCommunicator(String username, Boolean credssp) {
    if (this.type.startsWith('windows')) {
      this.builder.communicator = 'winrm'
      this.builder.winrm_username = 'Administrator'
      this.builder.windows_password_timeout = '20m'
      if (credssp) {
        this.builder.user_data_file = 'setup_winrm_credssp.ps1'
      } else {
        this.builder.user_data_file = 'setup_winrm.ps1'
      }
      
    } else {
      this.builder.communicator = 'ssh'
      this.builder.ssh_username = username
      this.builder.ssh_pty = true
      this.builder.ssh_timeout = '5m'
      this.builder.temporary_key_pair_type = 'ed25519'
    }
  }

  public void addBlockDevice(String deviceName, String volumeSize) {
    def block_device_mapping = [
      device_name: deviceName,
      volume_size: volumeSize,
      delete_on_termination: true,
      volume_type: "gp2"
    ]
    this.builder.ami_block_device_mappings = [block_device_mapping]
    this.builder.launch_block_device_mappings = [block_device_mapping]
  }

  public void addChefSoloProvisioner(List runList, String json, String version, Boolean useCinc=false) {
    def chefProvisioner = [
      type: 'chef-solo',
      chef_license: 'accept-silent',
      run_list: runList
    ]

    if (json) {
      chefProvisioner.json = json
    }
    if (version) {
      chefProvisioner.version = version
    }

    if (this.type.startsWith('windows')) {
      chefProvisioner.remote_cookbook_paths = ["C:/chef/cookbooks"]
      chefProvisioner.guest_os_type = 'windows'
      this.provisioners.push([
        type: 'powershell',
        inline: [
          "New-Item c:/chef/cookbooks -type directory -force",
          "New-Item c:/chef/environments -type directory -force"
        ]
      ])

      if (useCinc) {
        def cincInstallCommand = '[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; . { iwr -useb https://omnitruck.cinc.sh/install.ps1 } | iex; install'

        if(version) {
          chefProvisioner.install_command = 'powershell.exe -ExecutionPolicy Bypass -c "' + cincInstallCommand + ' -version ' + version + '"'
        } else {
          chefProvisioner.install_command = 'powershell.exe -ExecutionPolicy Bypass -c "' + cincInstallCommand + '"'
        }
        chefProvisioner.execute_command = 'C:/cinc-project/cinc/bin/chef-solo.bat --no-color -c C:/Windows/Temp/packer-chef-solo/solo.rb -j C:/Windows/Temp/packer-chef-solo/node.json'
      }

    } else {
      // linux
      chefProvisioner.remote_cookbook_paths = ["/etc/chef/cookbooks"]
      this.provisioners.push([
        type: 'shell',
        inline: [
          "sudo mkdir -p /etc/chef",
          "sudo chmod -R 777 /etc/chef"
        ]
      ])

      this.provisioners.push([
        type: 'file',
        source: '{{pwd}}/cookbooks',
        destination: '/etc/chef'
      ])

      this.provisioners.push([
        type: 'file',
        source: '{{pwd}}/environments',
        destination: '/etc/chef'
      ])

      if (useCinc) {
        chefProvisioner.install_command = "curl -L https://omnitruck.cinc.sh/install.sh | sudo bash -s --"
        if (version) {
          chefProvisioner.install_command += " -v ${version}"
        }
      }
    }

    this.provisioners.push(chefProvisioner)
  }

  public void addAnsibleInstallProvisioner(ArrayList installCommand, String playbookDirectory, String sshUser) {
    this.provisioners.push([
      type: 'shell',
      inline: [
        "sudo mkdir -p ${playbookDirectory}",
        "sudo chmod 755 ${playbookDirectory}",
        "sudo chown ${sshUser}:${sshUser} ${playbookDirectory}"
      ]
    ])
    this.provisioners.push([
      type: 'shell',
      inline: installCommand
    ])
  }

  public void addAnsibleLocalProvisioner(playbookFiles, playbookDir, amiPlaybookDirectory, cleanPlaybookDirectory, extraArguments) {
    Map provisioner = [
      type: 'ansible-local',
      playbook_files: playbookFiles,
      playbook_dir: playbookDir,
      staging_directory: amiPlaybookDirectory
    ]

    if (cleanPlaybookDirectory) {
      provisioner['clean_staging_directory'] = cleanPlaybookDirectory
    }

    if (extraArguments) {
      provisioner['extra_arguments'] = extraArguments
    }
    
    this.provisioners.push(provisioner)
  }

  public void addDownloadCookbookProvisioner(String bucket, String region, String path, String script = 'download_cookbooks.ps1') {
    if (this.type.startsWith('windows')) {
      Map provisioner = [
        type: 'powershell',
        script: script,
        environment_vars: [
          "SOURCE_BUCKET=${bucket}",
          "BUCKET_REGION=${region}",
          "COOKBOOK_PATH=${path}"
        ]
      ]
      this.provisioners.push(provisioner)
    }
  }

  public void addInstall7zipProvisioner(String script = 'install_7zip.ps1') {
    if (this.type.startsWith('windows')) {
      Map provisioner = [
        type: 'powershell',
        script: script
      ]
      this.provisioners.push(provisioner)
    }
  }

  public void addAmazonConfigProvisioner() {
    if (this.type.equals('windows')) {
      this.provisioners.push([
        type: 'powershell',
        script: 'ec2_launch_config.ps1'
      ])
      this.provisioners.push([
        type: 'powershell',
        inline: ['C:/ProgramData/Amazon/EC2-Windows/Launch/Scripts/InitializeInstance.ps1 -Schedule']
      ])
    } else if (this.type.equals('windows2012')) {
      this.provisioners.push([
        type: 'powershell',
        script: 'ec2_config_service.ps1'
      ])
    }

  }

  public void addAmazonEc2LaunchV2Provisioner() {
    if (this.type.equals('windows')) {
      this.provisioners.push([
        type: 'powershell',
        inline: ["& 'C:/Program Files/Amazon/EC2Launch/EC2Launch.exe' reset"]
      ])
      this.provisioners.push([
        type: 'powershell',
        inline: ["& 'C:/Program Files/Amazon/EC2Launch/EC2Launch.exe' sysprep"]
      ])
    }
  }

  public void addUninstallCincProvisioner() {
    if (this.type.startsWith('windows')) {
      this.provisioners.push([
        type: 'powershell',
        script: 'uninstall_cinc.ps1'
      ])
    }
  }

  public String toJson() {
    Map template = [
      builders: [],
      provisioners: [],
      "post-processors": [
        [
          type: 'manifest',
          output: "${this.id}.manifest.json",
          strip_path: true
        ]
      ]
    ]

    template.builders.push(this.builder)
    template.provisioners = this.provisioners

    return JsonOutput.prettyPrint(JsonOutput.toJson(template))
  }
}
