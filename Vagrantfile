# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure(2) do |config|
  config.vm.box = "ubuntu/trusty64"

  config.vm.provider "virtualbox" do |vb|
     vb.memory = "2048"
  end

  config.vm.network "forwarded_port", guest: 2375, host: 2375
  config.vm.network "forwarded_port", guest: 5000, host: 5000

  config.vm.provision 'shell', inline: <<-SH
    sudo echo DOCKER_OPTS=\\"-H unix:///var/run/docker.sock -H tcp://0.0.0.0:2375\\" > /etc/default/docker
  SH

  config.vm.provision 'docker' do |d|
    d.run 'registry', args: '-p 5000:5000'
    d.pull_images 'busybox'
  end
end
