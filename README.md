# vso-vmware

# To build Java code

    - Ensure you have instilled the maven
    - compile - mvn compile
    - run test  -   mvn test    
    - code coverage -   mvn test verify
            Go to _build ( _build\testReports\codecoverage ) folder on root directory to check the code coverage

# To build JS / TS  code
    
    - Ensure you have node.js installed
    - code build - gulp
    - test run - gulp test
    
# To package
    
    - Run 'gulp'.
    - Run 'mvn package'.
    - Clean node modules and restore production packages with 'npm install --production'.
    - Copy node_modules to build directory.
    - Deploy task with tfx.
    
# To run tests

    - Create a new environment variable 'VCENTER_URL' with the address of your vCenter server. (ex. 'https://192.168.0.42')
    - You may have to restart your IDE so that it picks up the new environment variable value
    - Update .\tests\Tools\vmOpsTool\TestResource.java and add your VMware vSphere connection information and test resources
    - The vCenter server should have at least one VMware host with the following minimum system requirements:
        - 100 GB hard disk in data store (depending on the size of the virtual machines below)
        - 4 GB RAM
        - 1 Datacenter
        - 1 Cluster
        - 1 Resource Pool
        - 4 virtual machines:
            - Linux (with VMware guest tools installed)
            - Windows (with VMware guest tools installed)
            - 2 arbitrary virtual machines with duplicate names
        - 2 virtual machine templates:
            - Linux (with VMware guest tools installed)
            - Windows (with VMware guest tools installed)