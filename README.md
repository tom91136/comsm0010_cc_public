 CND
 ===

Prerequisites:

 * Java 11
 * Python 2

To run the main CND server program (which also deploys the clients), run the following in the `cnd` project directory:

    ./sbt "runMain net.kurobako.cnd.Main --block COMSM0010cloud --d 30 --c 50.0 --timeout 30min --retries 10 --credential credentials.properties --keys cnd.pem"

The files `cnd.pem` and `credentials.properties` should be supplied. 

Help can be accessed via:

    ./sbt "runMain net.kurobako.cnd.Main --help"

    cnd 1.0.0
    Usage: cnd [options]

    --help                prints this usage text
    --block <value>       the block to use
    --d <value>           the difficulty
    --n <value>           the number of instances to start, this is mutually exclusive with the -c option
    --c <value>           the confidence level, this is mutually exclusive with the -n option
    --timeout <value>     the max timeout
    --retries <value>     the block to use
    --credential <value>  AWS IAM credentials file , see https://docs.aws.amazon.com/general/latest/gr/aws-sec-cred-types.html;
    The file needs to follow this format:
            accessKey=<your access key>
            secretKey=<your secret>
            keyName=<your pem key name>
            securityGroup=sg-<your-id>
    
    --keys <value>        .pem key files for the EC2 instances, see https://docs.aws.amazon.com/general/latest/gr/aws-sec-cred-types.html

The `nonce_finder` directory contains the C++ program for the extension. 
A copy of the compiled binary has already been included in the `cnd`.

To build the binary, in the `nonce_finder` project directory, do the following:
    
    cmake -Bbuild -H.  -DCMAKE_BUILD_TYPE=Release 

Followed by:


    cmake --build build --target nonce_finder --config Release

A binary should be generated at `nonce_finder`
    