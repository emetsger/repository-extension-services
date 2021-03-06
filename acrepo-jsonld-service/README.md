Repository JSON-LD handling service
===================================

This service transforms JSON-LD documents, either expanding or compacting them.
The service can be used with any camel route in an OSGi container.

Building
--------

To build this project use

    mvn install

Deploying in OSGi
-----------------

Each of these projects can be deployed in an OSGi container. For example using
[Apache Karaf](http://karaf.apache.org) version 4.x or better, you can run the following
command from its shell:

    feature:repo-add mvn:edu.amherst.acdc/acrepo-karaf/LATEST/xml/features
    feature:install acrepo-jsonld-service

Or by copying any of the compiled bundles into `$KARAF_HOME/deploy`.

