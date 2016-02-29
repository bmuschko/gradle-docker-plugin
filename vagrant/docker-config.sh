#!/bin/sh

sudo echo DOCKER_OPTS=\"-H unix:///var/run/docker.sock -H tcp://0.0.0.0:2375\" > /etc/default/docker
sudo service docker restart