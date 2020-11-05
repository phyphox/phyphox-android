# phyphox: Android

Phyphox is an app that uses the sensors in a smartphone for physics experiments. You can find additional details and examples on https://phyphox.org.

Copyright 2016 Dr. Sebastian Staacks, 2nd Institute of Physics, RWTH Aachen University.

This project has been created at the RWTH Aachen University and is released under the GNU General Public Licence (see licence file) since version 1.1.0.

**The names "phyphox" and "RWTH Aachen University" as well as the RWTH Aachen logo are registered trademarks.**

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/de.rwth_aachen.phyphox/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=de.rwth_aachen.phyphox)

## Coding style

The app and all of its parts are developed by students and researchers who do not necessarily have a software development background. Therefore, you will find many passages in our code that is not best practice. Any help in improving our code is welcome.

## Structure

This repository contains the source for the Android version of the app. The whole project is spread across several repositories:

* **phyphox-android**
  Android source, includes phyphox-experiments and phyphox-webinterface as subrepositories

* **phyphox-experiments**
  Phyphox experiment definitions, which are provided with the app

* **phyphox-ios**
  iOS source, includes phyphox-experiments and phyphox-webinterface as subrepositories

* **phyphox-translation**
  This contains the translations from experiment definitions and app store entries. It is synchronized manually to the experiments repository through a python script. Its main purpose is to conveniently provide translatable resources to our translation system.

* **phyphox-webeditor**
  The web-based editor to create and modify phyphox experiment-files in a GUI

* **phyphox-webinterface**
  This is the webinterface served by the webserver in the app when the "remote access" feature is activated

## Branches

We keep the code of the most recent published version in "master", while minor development is done in "development". Larger changes and long-term development occurs in additional branches, which at some point converge in a "dev-next" branch. In some repositories you will also find a "translation" branch, which usually is identical or very close to the current "development" or "dev-next" branch and linked to our translation system to control when our translators are able to work on new text passages.

## Contributing

We encourage any contribution to our project. However, due to the complexity of the project and the fact that it is used in schools around the world, there are some things to consider before any code makes it into the final version of phyphox that is distributed in the app stores:
* Be careful about changes of the UI. Many teachers rely on a simple and consistent workflow without too much distraction for their students. Also, they might have created some worksheets, which need updates when the interface changes. Therefore, try to add new features in a simple and lean way.
* Android and iOS versions should remain as similar as possible. We do accept slight variations of the UI of both versions if they follow the obvious design standards of each platform (for example using checkmarks on Android but buttons telling the action on iOS, or a FAB on Android and a Actionbar entry on iOS) and one version might get features that are impossible on the other platform (for example reading the light sensor on Android, which cannot be done on iOS or getting the number of satellites for GPS on Android). But if you provide a new feature that can be implemented on the other platform as well, we will not include it in the final app until we (or you or somebody) has ported it to the other platform as well. Once again, this app is used in classes around the world and we want to provide a very similar experience on both platforms, so the teachers don't have to explain the usage of phyphox twice.
* Translation is not done via git directly. If you want to translate the app, contact us, so we can set up an account for you on our translation system.
In any case, if you plan on contributing more than a little bugfix or optimization, it is probably a good idea to contact us first, so we can plan together and consider your plans in our development as well.

## Used libraries

### FFTW

This part of phyphox uses the fftw to calculate Fourier transformations (http://www.fftw.org). FFTW is distributed under the GNU General Public Licence (v2 or newer, so we use v3 to be compatible to the phyphox licence). FFTW is Copyright © 2003, 2007-11 Matteo Frigo, Copyright © 2003, 2007-11 Massachusetts Institute of Technology.

### Apache POI

This part of phyphox uses the Apache POI library (https://poi.apache.org) to create the webserver for the remote access feature. It is released under the Apache Licence v2 with the following notices:

Apache POI
Copyright 2003-2017 The Apache Software Foundation

This product includes software developed by
The Apache Software Foundation (https://www.apache.org/).

This product contains parts that were originally based on software from BEA.
Copyright (c) 2000-2003, BEA Systems, <http://www.bea.com/> (dead link),
which was acquired by Oracle Corporation in 2008.
<http://www.oracle.com/us/corporate/Acquisitions/bea/index.html>
<https://en.wikipedia.org/wiki/BEA_Systems>

This product contains W3C XML Schema documents. Copyright 2001-2003 (c)
World Wide Web Consortium (Massachusetts Institute of Technology, European
Research Consortium for Informatics and Mathematics, Keio University)

This product contains the Piccolo XML Parser for Java
(http://piccolo.sourceforge.net/). Copyright 2002 Yuval Oren.

This product contains the chunks_parse_cmds.tbl file from the vsdump program.
Copyright (C) 2006-2007 Valek Filippov (frob@df.ru)

This product contains parts of the eID Applet project 
<http://eid-applet.googlecode.com> and <https://github.com/e-Contract/eid-applet>.
Copyright (c) 2009-2014
FedICT (federal ICT department of Belgium), e-Contract.be BVBA (https://www.e-contract.be),
Bart Hanssens from FedICT

### ZXing

This part of phyphox uses the ZXing (Zebra Crossing) library to read QR codes (https://github.com/zxing/zxing). It is licenced under the Apache Licence v2 with the following notices:

**NOTICES FOR BARCODE4J**

Barcode4J
Copyright 2002-2010 Jeremias Märki
Copyright 2005-2006 Dietmar Bürkle

Portions of this software were contributed under section 5 of the
Apache License. Contributors are listed under:
http://barcode4j.sourceforge.net/contributors.html

**NOTICES FOR JCOMMANDER**

Copyright 2010 Cedric Beust cedric@beust.com

### Eclipse Paho MQTT

This part of phyphox uses the Eclipse Paho MQTT library for MQTT communication (https://www.eclipse.org/paho), licenced under the Eclipse Distribution Licence 1.0 (please see the webpage for details on the licence and contributors).

Eclipse Distribution License - v 1.0

Copyright (c) 2007, Eclipse Foundation, Inc. and its licensors.

All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
* Neither the name of the Eclipse Foundation, Inc. nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission. 

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

