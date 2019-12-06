## nonce_finder

Gotta find the nonce!

Build dependencies:

 * CMake >= 3.12
 * GCC/Clang/MSVC
 

Library dependencies:
 * libatomic (Fedora only)
 
 First setup: 
 
    cmake -Bbuild -H. -DCMAKE_BUILD_TYPE=Release

Then build:
    
    cmake --build build --target nonce_finder --config Release


## Licence

    Copyright 2019 WEI CHEN LIN
    
    Licensed under the Apache License, Version 2.0 (the "License");e "License");
    you may not use this file except in compliance with the License. the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0writing, software
    "AS IS" BASIS,
    Unless required by applicable law or agreed to in writing, softwarer express or implied.
    distributed under the License is distributed on an "AS IS" BASIS, permissions and
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
