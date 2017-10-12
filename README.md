# Tractor ![Build Status](https://travis-ci.org/metalmynds/tractor.svg?branch=master)
Amazon Device Farm API Wrapper for Executing Tests and Capturing Artifacts.

This wrapper is heavily based upon the [Jenkins Device Farm Plugin](https://github.com/awslabs/aws-device-farm-jenkins-plugin).

The wrapper has been packaged outside the Jenkins Plugin Project as its not possible to reference the artifact from Maven.

It is intended to be used to create a maven and gradle plugin (that has no dependencies on the android ndk) or simply to execute tests programmatically.
