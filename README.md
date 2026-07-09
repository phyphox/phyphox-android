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
  
The overarching documentation (for example of the phyphox file format or the REST API) can be found in our [Wiki on phyphox.org](https://phyphox.org/wiki).

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

### jlhttp

This part of phyphox uses the jlhttp library ([freeutils.net/source/jlhttp](https://www.freeutils.net/source/jlhttp/)) to create the webserver for the remote access feature. It is released under the GNU General Public Licence. Big thanks to [Amichai R.](https://github.com/amichair) for this library and for migrating phyphox to it.

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

The Android version's MQTT communication uses the MQTT Android Service by Hannes Achleitner (https://github.com/hannesa2/paho.mqtt.android, Apache Licence 2.0), which in turn uses the Eclipse Paho MQTT library (https://www.eclipse.org/paho, Eclipse Distribution Licence 1.0), please see the respective webpages for details on the licence and contributors.

