# web-service

## 构建  
```
export JAVA_HOME=/Users/yaoqi.li/Documents/harmless/java/jdk-17.0.6.jdk/Contents/Home\n
mvn clean package
docker build -t data-service:latest .

```



{
"fact": {
"type": "DeviceData",
"build_date": "2025-10-28 09:05:28",
"header": {
"guid": "6c405db8-a909-415d-b72a-398db96e9427",
"platform": "android",
"app_id": "1",
"sdk_version": "1.0.0.220519"
},
"common": {
"user_id": "",
"guid": "6c405db8-a909-415d-b72a-398db96e9427",
"start_id": 1761613527488,
"sdk_version": "1.0.0.220519",
"app_id": 1,
"app_name": "x-genie终端",
"app_package": "com.coolxer.probe.demo",
"app_version": "1.0",
"platform": "android",
"manufacturer": "Google",
"model": "Pixel 3",
"system": "Android",
"system_version": "12",
"net_type": "Wi-Fi",
"lan_ip": "192.168.142.239",
"wan_ip": "0.0.0.0",
"latitude": -1.0,
"longitude": -1.0,
"country": "",
"province": "",
"city": "",
"county": "",
"thoroughfare": "",
"client_time": "2025-10-28 09:05:27"
},
"device": {
"type": "Phone",
"name": "Pixel 3",
"system_name": "Android",
"system_version": "12",
"build_id": "SP1A.210812.016.C1",
"model_code": "Pixel 3",
"country_code": "CN",
"language": "zh",
"time_zone": "Asia/Shanghai",
"currency": "¥",
"start_elapsed_time": "1970-01-08 20:25:01",
"wire_connected": false
},
"screen": {
"width": 1080,
"height": 2160,
"brightness": 0.0,
"density": 2.75,
"rotation": 0
},
"cpu": {
"total_core": 8,
"used_core": 8,
"type": "arm64-v8a",
"abi": "arm64-v8a,armeabi-v7a,armeabi"
},
"battery": {
"PROPERTY_STATUS": 3,
"PROPERTY_CHARGE_COUNTER": 2284000,
"PROPERTY_CURRENT_AVERAGE": 268437,
"PROPERTY_CURRENT_NOW": 865000,
"PROPERTY_CAPACITY": 86,
"PROPERTY_ENERGY_COUNTER": -2147483648,
"EXTRA_LEVEL": 86,
"EXTRA_SCALE": 100,
"EXTRA_PLUGGED": 0,
"EXTRA_TEMPERATURE": 196,
"EXTRA_PRESENT": true,
"EXTRA_TECHNOLOGY": "Li-ion",
"capacity": 2915,
"charging": 0
},
"uuid": {
"mac_wlan0": "null",
"mac_wlan1": "null",
"mac_p2p0": "null",
"imei": "",
"imsi": "",
"iccid": "",
"serial": "",
"file_uid_data": "0000fd0400000000-13520379-13193728",
"file_uid_system": "1d3e6840bb739d0c-213588-2800",
"file_uid_cache": "",
"file_uid_vendor": "3d23fe7591865e2f-111729-2752",
"android_id": "cdd50269595aa11e",
"widevine_id": "18e5396a4417448d78f32d889dc2bb1c",
"oaid": "badcc1f93d21026a7fc5257990b5b2fc",
"boot_id": "2f880013-4b2c-491a-b207-d6d838db566b",
"guid": "6c405db8-a909-415d-b72a-398db96e9427"
},
"network": {
"sim_count": 0,
"cell_type": "IWLAN",
"operators": "",
"country": "cn",
"mcc": null,
"iso": "cn",
"mnc": null,
"allows_voip": "false",
"lan_ip": "192.168.142.239",
"mask": "255.255.255.255",
"broadcast": "192.168.142.255",
"routing": "Interface: r_rmnet_data0\nIP: fe80::6a8e:9f5c:b46b:98ea%r_rmnet_data0\n",
"signal": "0",
"type": "Wi-Fi"
},
"storage": {
"total_disk": 5.5379472384E10,
"used_disk": 4.609050624E10,
"total_ram": 3.753299968E9,
"used_ram": 2.262704128E9
},
"opengl": {
"GL_VENDOR": "Qualcomm",
"GL_VERSION": "OpenGL ES 3.2 V@0490.0 (GIT@781e7d0, I46ff5fc46f, 1606819536) (Date:12/01/20)",
"GL_RENDERER": "Adreno (TM) 630",
"GL_SHADING_LANGUAGE_VERSION": "OpenGL ES GLSL ES 3.20",
"EGL_VENDOR": "Android",
"EGL_VERSION": "1.5 Android META-EGL",
"EGL_CLIENT_APIS": "OpenGL_ES"
},
"prop": {
"roBuildUser": "android-build",
"roBuildVersionIncremental": "8029091",
"roBuildFingerprint": "google/blueline/blueline:12/SP1A.210812.016.C1/8029091:user/release-keys",
"roBuildDisplayId": "SP1A.210812.016.C1",
"roBuildDescription": "blueline-user 12 SP1A.210812.016.C1 8029091 release-keys",
"roBuildHost": "abfarm-release-rbe-64-00083",
"roBootHardware": "blueline",
"roBuildTags": "release-keys",
"roBuildType": "user",
"roSimulatedPhone": "",
"roRender": "",
"roDebuggable": "0",
"roSecure": "1"
},
"build": {
"TIME": "1640393541000",
"FINGERPRINT": "google/blueline/blueline:12/SP1A.210812.016.C1/8029091:user/release-keys",
"MODEL": "Pixel 3",
"MANUFACTURER": "Google",
"HARDWARE": "blueline",
"PRODUCT": "blueline",
"BOARD": "blueline",
"BOOTLOADER": "b1c1-0.4-7617406",
"SERIAL": "unknown",
"DEVICE": "blueline",
"HOST": "abfarm-release-rbe-64-00083",
"TAGS": "release-keys"
}
},
"rule": "AndroidDeviceFactRule.groovy",
"agendas": [],
"punishes": [],
"server_time": "2025-10-28 09:05:52"
}



{
"fact": {
"type": "AppData",
"build_date": "2025-10-28 09:05:29",
"header": {
"guid": "6c405db8-a909-415d-b72a-398db96e9427",
"platform": "android",
"app_id": "1",
"sdk_version": "1.0.0.220519"
},
"common": {
"user_id": "",
"guid": "6c405db8-a909-415d-b72a-398db96e9427",
"start_id": 1761613527488,
"sdk_version": "1.0.0.220519",
"app_id": 1,
"app_name": "x-genie终端",
"app_package": "com.coolxer.probe.demo",
"app_version": "1.0",
"platform": "android",
"manufacturer": "Google",
"model": "Pixel 3",
"system": "Android",
"system_version": "12",
"net_type": "Wi-Fi",
"lan_ip": "192.168.142.239",
"wan_ip": "0.0.0.0",
"latitude": -1.0,
"longitude": -1.0,
"country": "",
"province": "",
"city": "",
"county": "",
"thoroughfare": "",
"client_time": "2025-10-28 09:05:27"
},
"installed": [
{
"app_name": "com.android.ons",
"package_name": "com.android.ons",
"version_name": "12",
"version_code": "31",
"md5": "530996ed9d8bfcf50ceebdcb6aa2a595",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267981,
"from": "packages"
},
{
"app_name": "数字健康",
"package_name": "com.google.android.apps.wellbeing",
"version_name": "1.0.381222135",
"version_code": "276637",
"md5": "0489af3b026e798acfd07f8996ec8bf9",
"cert_md5": "5d7f145e1d808cc8e95c4c78241ab37f",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 550223429,
"from": "packages"
},
{
"app_name": "USCC",
"package_name": "com.android.sdm.plugins.usccdm",
"version_name": "1.0",
"version_code": "1",
"md5": "7c6b33481ee13acfbb18db91df068ab7",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545832517,
"from": "packages"
},
{
"app_name": "com.google.SSRestartDetector",
"package_name": "com.google.SSRestartDetector",
"version_name": "12",
"version_code": "31",
"md5": "320197a2dade9ca17eac9ad956b7892b",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545832525,
"from": "packages"
},
{
"app_name": "Carrier OMADM",
"package_name": "com.android.sdm.plugins.dcmo",
"version_name": "12",
"version_code": "31",
"md5": "6b66e1db7a8a154e6c2e36be40f23d50",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545832517,
"from": "packages"
},
{
"app_name": "电话服务",
"package_name": "com.android.phone",
"version_name": "12",
"version_code": "31",
"md5": "678e9d2913feba9e833d23b697c6510b",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 952647245,
"from": "packages"
},
{
"app_name": "PacProcessor",
"package_name": "com.android.pacprocessor",
"version_name": "12",
"version_code": "31",
"md5": "20ed82815195a24e864d04704814cdf1",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267973,
"from": "packages"
},
{
"app_name": "MmsService",
"package_name": "com.android.mms.service",
"version_name": "12",
"version_code": "31",
"md5": "3a1939702d184c3425e82072a70fddbd",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 948485701,
"from": "packages"
},
{
"app_name": "Android S Easter Egg",
"package_name": "com.android.egg",
"version_name": "1.0",
"version_code": "12",
"md5": "ee95a5ba61d88dff34f3987e76ea864d",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267973,
"from": "packages"
},
{
"app_name": "软件包安装程序",
"package_name": "com.google.android.packageinstaller",
"version_name": "12",
"version_code": "31",
"md5": "8652c9e6ec63ccea0c5ac4eb56b124a0",
"cert_md5": "0c0a20bbf895f4dcaea3dd103055d19d",
"cert_issuer": "EMAILADDRESS\u003dandroid@android.com, CN\u003dAndroid, OU\u003dAndroid, O\u003dAndroid, L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 818429509,
"from": "packages"
},
{
"app_name": "com.qualcomm.uimremoteserver",
"package_name": "com.qualcomm.uimremoteserver",
"version_name": "12",
"version_code": "31",
"md5": "df2c92b5df3756f19724d6b5120870bf",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545832517,
"from": "packages"
},
{
"app_name": "com.android.systemui.plugin.globalactions.wallet",
"package_name": "com.android.systemui.plugin.globalactions.wallet",
"version_name": "1.0.0.0",
"version_code": "10000000",
"md5": "c33205288e3287ee3e3b33979409ae85",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 818462277,
"from": "packages"
},
{
"app_name": "输入设备",
"package_name": "com.android.inputdevices",
"version_name": "12",
"version_code": "31",
"md5": "33a634c3f297e366ae241018222761d3",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267909,
"from": "packages"
},
{
"app_name": "Call Log Backup/Restore",
"package_name": "com.android.calllogbackup",
"version_name": "12",
"version_code": "31",
"md5": "132eb52c1764dc45e7af177c7f3727dd",
"cert_md5": "739a26bea9b29fcfce4caf666edd5da3",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814333509,
"from": "packages"
},
{
"app_name": "Live Wallpaper Picker",
"package_name": "com.android.wallpaper.livepicker",
"version_name": "12",
"version_code": "31",
"md5": "4e369b4f56461d26ab1ead14d77808d1",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 818462277,
"from": "packages"
},
{
"app_name": "日历存储",
"package_name": "com.android.providers.calendar",
"version_name": "12",
"version_code": "31",
"md5": "6e18be49524bfb29d0e9e849c11e7798",
"cert_md5": "dac54eebe49c138f40b805b9526ab161",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814235205,
"from": "packages"
},
{
"app_name": "Cell Broadcast Service",
"package_name": "com.google.android.cellbroadcastservice",
"version_name": "R-initial",
"version_code": "300000000",
"md5": "b29842cb28c021aa1ede8975bce89d45",
"cert_md5": "551004f246c7623f557504c86b824393",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "1970-01-01 08:00:00",
"update_time": "1970-01-01 08:00:00",
"application_info_flags": 680050253,
"from": "packages"
},
{
"app_name": "电话和短信存储",
"package_name": "com.android.providers.telephony",
"version_name": "12",
"version_code": "31",
"md5": "fe83f70468b44b73622744bfa4e7a375",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 1015791109,
"from": "packages"
},
{
"app_name": "外部存储设备",
"package_name": "com.android.externalstorage",
"version_name": "12",
"version_code": "31",
"md5": "7a1f15adf24788f53832768d31b8c8de",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267973,
"from": "packages"
},
{
"app_name": "Google 语音服务",
"package_name": "com.google.android.tts",
"version_name": "googletts.google-speech-apk_20210729.00_p0.387528199.tnt",
"version_code": "210321711",
"md5": "30c1810efb8ca208a83fd70ad714ec88",
"cert_md5": "cde9f6208d672b54b1dacc0b7029f5eb",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 550223429,
"from": "packages"
},
{
"app_name": "Carrier Setup",
"package_name": "com.google.android.carriersetup",
"version_name": "12",
"version_code": "31",
"md5": "f3f5ebde8397f053705aa733e64456ea",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545799749,
"from": "packages"
},
{
"app_name": "Device Health Services Adapter",
"package_name": "com.google.android.turboadapter",
"version_name": "12",
"version_code": "31",
"md5": "4913d86a5c2fe84b7be8dcfdd2246110",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814235205,
"from": "packages"
},
{
"app_name": "com.android.sdm.plugins.diagmon",
"package_name": "com.android.sdm.plugins.diagmon",
"version_name": "12",
"version_code": "31",
"md5": "3c6e06502443eb51f4d03c0657448200",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545832517,
"from": "packages"
},
{
"app_name": "Secure UI Service",
"package_name": "com.qualcomm.qti.services.secureui",
"version_name": "1.0",
"version_code": "1",
"md5": "6c10e6c22db6fba947cd973075751d59",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545832525,
"from": "packages"
},
{
"app_name": "Google Play 商店",
"package_name": "com.android.vending",
"version_name": "25.9.49-21 [0] [PR] 386309911",
"version_code": "82594910",
"md5": "140c71fbe8df3df2041e6efe8bd5d71c",
"cert_md5": "cde9f6208d672b54b1dacc0b7029f5eb",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 684375621,
"from": "packages"
},
{
"app_name": "电话",
"package_name": "com.android.server.telecom",
"version_name": "12",
"version_code": "31",
"md5": "0094bfc66b8b6861278ed3afa398b679",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 818429509,
"from": "packages"
},
{
"app_name": "ConnMO",
"package_name": "com.android.sdm.plugins.connmo",
"version_name": "1.0",
"version_code": "1",
"md5": "bed83c10019ac95933283561e5b32004",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545832517,
"from": "packages"
},
{
"app_name": "密钥链",
"package_name": "com.android.keychain",
"version_name": "12",
"version_code": "31",
"md5": "00dea770a7d7a6892e04716f58ae3d86",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814235205,
"from": "packages"
},
{
"app_name": "com.android.wallpaperbackup",
"package_name": "com.android.wallpaperbackup",
"version_name": "12",
"version_code": "31",
"md5": "ad3159efe5e6d46f716761d05f2d4337",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 881376773,
"from": "packages"
},
{
"app_name": "com.android.server.NetworkPermissionConfig",
"package_name": "com.google.android.networkstack.permissionconfig",
"version_name": "2019-09",
"version_code": "300000000",
"md5": "dbe099c98266b6cfd5c1f5b52373a134",
"cert_md5": "551004f246c7623f557504c86b824393",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 948485701,
"from": "packages"
},
{
"app_name": "联系人存储",
"package_name": "com.android.providers.contacts",
"version_name": "12",
"version_code": "31",
"md5": "fcfe3c6c463016a3a6c97082fab7e261",
"cert_md5": "739a26bea9b29fcfce4caf666edd5da3",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814235205,
"from": "packages"
},
{
"app_name": "工作设置",
"package_name": "com.android.managedprovisioning",
"version_name": "12",
"version_code": "31",
"md5": "5f37274acb190247fe47bfc833e84350",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 952679941,
"from": "packages"
},
{
"app_name": "声音",
"package_name": "com.android.soundpicker",
"version_name": "12",
"version_code": "31",
"md5": "0a592df7c92241d59cbaf4f90f8af073",
"cert_md5": "2f474023db835515daf065dea9ad350d",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 818429509,
"from": "packages"
},
{
"app_name": "com.android.providers.media",
"package_name": "com.android.providers.media",
"version_name": "12",
"version_code": "1024",
"md5": "3ea4c22c5795467d94801f4c04d82aca",
"cert_md5": "2f474023db835515daf065dea9ad350d",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 952647237,
"from": "packages"
},
{
"app_name": "MTP 主机",
"package_name": "com.android.mtp",
"version_name": "12",
"version_code": "31",
"md5": "c2cc3d3dc313586c540dc45e2373dc1d",
"cert_md5": "2f474023db835515daf065dea9ad350d",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 948452933,
"from": "packages"
},
{
"app_name": "SIM 卡工具包",
"package_name": "com.android.stk",
"version_name": "12",
"version_code": "31",
"md5": "ffa08ed0aeec7c3d23ee56bfe565c992",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267973,
"from": "packages"
},
{
"app_name": "com.android.sharedstoragebackup",
"package_name": "com.android.sharedstoragebackup",
"version_name": "12",
"version_code": "31",
"md5": "6dc249ac69ca53c69357a83a130bc35c",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814333445,
"from": "packages"
},
{
"app_name": "com.android.service.ims.RcsServiceApp",
"package_name": "com.android.service.ims",
"version_name": "2.4.6",
"version_code": "1",
"md5": "b2437c4b3b1f9c7140b0bff3dcf29f8b",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267981,
"from": "packages"
},
{
"app_name": "NFC服务",
"package_name": "com.android.nfc",
"version_name": "12",
"version_code": "31",
"md5": "b42fd4e192d56c7e142c64f272a9561b",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 281591373,
"from": "packages"
},
{
"app_name": "com.qualcomm.uimremoteclient",
"package_name": "com.qualcomm.uimremoteclient",
"version_name": "12",
"version_code": "31",
"md5": "38ab7156ba2e9a9588dcd06a3cf63d52",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545832517,
"from": "packages"
},
{
"app_name": "VpnDialogs",
"package_name": "com.android.vpndialogs",
"version_name": "12",
"version_code": "31",
"md5": "a3568d3a2f27648d6e248d7a4d9eaeec",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814235205,
"from": "packages"
},
{
"app_name": "Google 服务框架",
"package_name": "com.google.android.gsf",
"version_name": "12",
"version_code": "31",
"md5": "2b320e6c209615e79791bec32b1eaa6d",
"cert_md5": "f026fdcf21375f987164c84da76ef5fd",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 143146565,
"from": "packages"
},
{
"app_name": "Presence",
"package_name": "com.android.service.ims.presence",
"version_name": "12",
"version_code": "31",
"md5": "edb8d696a5446cc08c4c3498299d206e",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267973,
"from": "packages"
},
{
"app_name": "Emergency information",
"package_name": "com.android.emergency",
"version_name": "12",
"version_code": "31",
"md5": "8358133a89740d61b2a144ff146afa83",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267973,
"from": "packages"
},
{
"app_name": "Android 系统",
"package_name": "android",
"version_name": "12",
"version_code": "31",
"md5": "a28250a0c73039a31889baefa4030090",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 818462217,
"from": "packages"
},
{
"app_name": "运营商默认应用",
"package_name": "com.android.carrierdefaultapp",
"version_name": "12",
"version_code": "31",
"md5": "2dd504c5bb9feebff46efaeffe66173d",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 948485701,
"from": "packages"
},
{
"app_name": "Google Play 服务",
"package_name": "com.google.android.gms",
"version_name": "21.24.20 (190408-391796735)",
"version_code": "212420054",
"md5": "389a5b4779e368eeba70a46569cf7548",
"cert_md5": "f026fdcf21375f987164c84da76ef5fd",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": -1597325755,
"from": "packages"
},
{
"app_name": "SystemUIGX",
"package_name": "com.google.android.systemui.gxoverlay",
"version_name": "12",
"version_code": "31",
"md5": "15f52cf3d67948d494b7252b6c1b7f55",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267969,
"from": "packages"
},
{
"app_name": "RilConfig",
"package_name": "com.google.RilConfigService",
"version_name": "12",
"version_code": "31",
"md5": "0d61179211434b5619c5a4b25fd1064c",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545832517,
"from": "packages"
},
{
"app_name": "com.android.cellbroadcastreceiver",
"package_name": "com.android.cellbroadcastreceiver",
"version_name": "R-initial",
"version_code": "300000000",
"md5": "5c89935785d3d919f72e4eb9476949de",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267973,
"from": "packages"
},
{
"app_name": "一体化位置信息",
"package_name": "com.android.location.fused",
"version_name": "12",
"version_code": "31",
"md5": "533374428ffd9577aac01c1a5f0c9a6b",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267973,
"from": "packages"
},
{
"app_name": "Intent Filter Verification Service",
"package_name": "com.android.statementservice",
"version_name": "1.0",
"version_code": "1",
"md5": "e8cc41ca4fccf9c0a31a632448dbbf72",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814235205,
"from": "packages"
},
{
"app_name": "设备管理服务",
"package_name": "com.android.omadm.service",
"version_name": "1.0.0",
"version_code": "1",
"md5": "2cb6245a4c37582be6d10c3be5a72436",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545799749,
"from": "packages"
},
{
"app_name": "Dock Updater",
"package_name": "com.google.android.dreamlinerupdater",
"version_name": "12",
"version_code": "31",
"md5": "e9e3b9d7d4afddf2323a8315cf4aa461",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814235205,
"from": "packages"
},
{
"app_name": "com.android.localtransport",
"package_name": "com.android.localtransport",
"version_name": "12",
"version_code": "31",
"md5": "d88264b1ae49e2d1cdddbcbbacb8252f",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814235205,
"from": "packages"
},
{
"app_name": "超级省电模式",
"package_name": "com.google.android.flipendo",
"version_name": "12",
"version_code": "31",
"md5": "bc9445624c16d4f7c454afe4de3135dc",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 818658885,
"from": "packages"
},
{
"app_name": "Hidden Menu",
"package_name": "com.google.android.hiddenmenu",
"version_name": "101.20200603",
"version_code": "101",
"md5": "f5716e92c3242f478c28903b0cd535e9",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545799749,
"from": "packages"
},
{
"app_name": "证书安装程序",
"package_name": "com.android.certinstaller",
"version_name": "12",
"version_code": "31",
"md5": "b96157a234dee7dd9647625b9f3e500e",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814235205,
"from": "packages"
},
{
"app_name": "设置存储",
"package_name": "com.android.providers.settings",
"version_name": "12",
"version_code": "31",
"md5": "7ce003e007a4bfe7cbce0fe8a278c84d",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814398981,
"from": "packages"
},
{
"app_name": "com.qti.qualcomm.datastatusnotification",
"package_name": "com.qti.qualcomm.datastatusnotification",
"version_name": "12",
"version_code": "31",
"md5": "6533890bb101d578a16808b24c4ab7d5",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545832525,
"from": "packages"
},
{
"app_name": "系统界面",
"package_name": "com.android.systemui",
"version_name": "12",
"version_code": "31",
"md5": "6e60172b888ff1cecea0356104860488",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 550026765,
"from": "packages"
},
{
"app_name": "org.codeaurora.ims",
"package_name": "org.codeaurora.ims",
"version_name": "1.0",
"version_code": "1",
"md5": "31d9641b5d694a7d6b63b666b5476382",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545832517,
"from": "packages"
},
{
"app_name": "SecureElementApplication",
"package_name": "com.android.se",
"version_name": "12",
"version_code": "31",
"md5": "b672acb2be80c2a5a0a4024ea8bbfaba",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267981,
"from": "packages"
},
{
"app_name": "配套设备管理器",
"package_name": "com.android.companiondevicemanager",
"version_name": "12",
"version_code": "31",
"md5": "23195f336aa4fe0526472939e3c70f71",
"cert_md5": "dac54eebe49c138f40b805b9526ab161",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 818429509,
"from": "packages"
},
{
"app_name": "ProxyHandler",
"package_name": "com.android.proxyhandler",
"version_name": "12",
"version_code": "31",
"md5": "e442ff8ea4a1aa0e669fc9f94b446f10",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267973,
"from": "packages"
},
{
"app_name": "x-genie终端",
"package_name": "com.coolxer.probe.demo",
"version_name": "1.0",
"version_code": "1",
"md5": "",
"cert_md5": "123c5efb69c00003c0d9bcb4f6aabf91",
"cert_issuer": "C\u003dUS, O\u003dAndroid, CN\u003dAndroid Debug",
"install_time": "2024-03-20 20:19:20",
"update_time": "2025-10-22 11:37:32",
"application_info_flags": 818462534,
"from": "packages"
},
{
"app_name": "SprintDM",
"package_name": "com.android.sdm.plugins.sprintdm",
"version_name": "1.0",
"version_code": "1",
"md5": "b2a12e45fabb8f3cba179973c4f8211d",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545799749,
"from": "packages"
},
{
"app_name": "权限控制器",
"package_name": "com.google.android.permissioncontroller",
"version_name": "s_aml_310733000",
"version_code": "310733000",
"md5": "3b245da72aaa229def21d6c5ee25b0d1",
"cert_md5": "21cb7d2befc33835df9d5071b299e02b",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "1970-01-01 08:00:00",
"update_time": "1970-01-01 08:00:00",
"application_info_flags": 549993989,
"from": "packages"
},
{
"app_name": "Tethering",
"package_name": "com.google.android.networkstack.tethering",
"version_name": "12-7653768",
"version_code": "31",
"md5": "3e344f220de35838c3053fab7fa5f022",
"cert_md5": "551004f246c7623f557504c86b824393",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "1970-01-01 08:00:00",
"update_time": "1970-01-01 08:00:00",
"application_info_flags": 680050253,
"from": "packages"
},
{
"app_name": "内容下载管理器",
"package_name": "com.android.providers.downloads",
"version_name": "12",
"version_code": "31",
"md5": "e66f288536f573e4188477eff2d0115b",
"cert_md5": "2f474023db835515daf065dea9ad350d",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 952647237,
"from": "packages"
},
{
"app_name": "OemDmTrigger",
"package_name": "com.google.omadm.trigger",
"version_name": "1.0",
"version_code": "1",
"md5": "04687637fb936c04ff32c250f4dc1b6c",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545799749,
"from": "packages"
},
{
"app_name": "Android Services Library",
"package_name": "com.google.android.ext.services",
"version_name": "s_aml_310727000",
"version_code": "310727000",
"md5": "3b14c1c17991729c4a91eb2083915412",
"cert_md5": "19f24a7df7f27b58374ad3a1d082a356",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "1970-01-01 08:00:00",
"update_time": "1970-01-01 08:00:00",
"application_info_flags": 550026821,
"from": "packages"
},
{
"app_name": "com.quicinc.cne.CNEService.CNEServiceApp",
"package_name": "com.quicinc.cne.CNEService",
"version_name": "1.1",
"version_code": "1",
"md5": "1fac97f1474743ed66bc7467242f20a0",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545832525,
"from": "packages"
},
{
"app_name": "媒体存储设备",
"package_name": "com.google.android.providers.media.module",
"version_name": "12-7639396",
"version_code": "31",
"md5": "f197d7f29acd56433067ededcce16ae8",
"cert_md5": "a8c783df6f17b8dfb06d0742fb64463f",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "1970-01-01 08:00:00",
"update_time": "1970-01-01 08:00:00",
"application_info_flags": 684211781,
"from": "packages"
},
{
"app_name": "com.qualcomm.qcrilmsgtunnel",
"package_name": "com.qualcomm.qcrilmsgtunnel",
"version_name": "12",
"version_code": "31",
"md5": "ffc0b5be3c0bdf607ac81e63ed535317",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545832517,
"from": "packages"
},
{
"app_name": "com.qualcomm.atfwd",
"package_name": "com.qualcomm.atfwd",
"version_name": "12",
"version_code": "31",
"md5": "22d42101c80b76329bc0ba6661742fe9",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545832517,
"from": "packages"
},
{
"app_name": "蓝牙",
"package_name": "com.android.bluetooth",
"version_name": "12",
"version_code": "31",
"md5": "00702490dfa533216075e2c5ded1a0fa",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 818462277,
"from": "packages"
},
{
"app_name": "下载",
"package_name": "com.android.providers.downloads.ui",
"version_name": "12",
"version_code": "31",
"md5": "f048f775eb87e9ba7400cd693ca0c99d",
"cert_md5": "2f474023db835515daf065dea9ad350d",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 952680005,
"from": "packages"
},
{
"app_name": "网络管理器",
"package_name": "com.google.android.networkstack",
"version_name": "s_aml_310727000",
"version_code": "310727000",
"md5": "beab12c6b09559a94f1b87c59be225da",
"cert_md5": "551004f246c7623f557504c86b824393",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 680050253,
"from": "packages"
},
{
"app_name": "Shell",
"package_name": "com.android.shell",
"version_name": "12",
"version_code": "31",
"md5": "916762282c2f1db35bf023f215f7bf12",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267973,
"from": "packages"
},
{
"app_name": "存储已屏蔽的号码",
"package_name": "com.android.providers.blockednumber",
"version_name": "12",
"version_code": "31",
"md5": "03b2a912a88250ae357c253effd871cf",
"cert_md5": "739a26bea9b29fcfce4caf666edd5da3",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267973,
"from": "packages"
},
{
"app_name": "uceShimService",
"package_name": "com.qualcomm.qti.uceShimService",
"version_name": "12",
"version_code": "31",
"md5": "7a410ef9b0ffba63d74b266b1187361c",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 143179341,
"from": "packages"
},
{
"app_name": "X-Divert设置",
"package_name": "com.qti.xdivert",
"version_name": "12",
"version_code": "31",
"md5": "be604905e3bbb63d00e16ff52cd49135",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545832517,
"from": "packages"
},
{
"app_name": "com.android.backupconfirm",
"package_name": "com.android.backupconfirm",
"version_name": "12",
"version_code": "31",
"md5": "20ea09cbe51bb1eb4bfe4af0a69765b7",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814235141,
"from": "packages"
},
{
"app_name": "com.qualcomm.timeservice",
"package_name": "com.qualcomm.timeservice",
"version_name": "12",
"version_code": "31",
"md5": "8d0942b8b5cf84019dd4394e6f9658a8",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545832517,
"from": "packages"
},
{
"app_name": "设置",
"package_name": "com.android.settings",
"version_name": "12",
"version_code": "31",
"md5": "6c141508293fa3951c8dc857ee9aa5a8",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 684310085,
"from": "packages"
},
{
"app_name": "用户字典",
"package_name": "com.android.providers.userdictionary",
"version_name": "12",
"version_code": "31",
"md5": "0f62c28429aaa9192671ceb2921b25c1",
"cert_md5": "739a26bea9b29fcfce4caf666edd5da3",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267909,
"from": "packages"
},
{
"app_name": "Dynamic System Updates",
"package_name": "com.android.dynsystem",
"version_name": "12",
"version_code": "31",
"md5": "b99562adf81bd4a7f6f4c24ac884bacd",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814235205,
"from": "packages"
},
{
"app_name": "com.qualcomm.qti.telephonyservice",
"package_name": "com.qualcomm.qti.telephonyservice",
"version_name": "12",
"version_code": "31",
"md5": "b9865df58074791cd6924f562e109b31",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 545832525,
"from": "packages"
},
{
"app_name": "CameraExtensionsProxy",
"package_name": "com.android.cameraextensions",
"version_name": "12",
"version_code": "31",
"md5": "10ebb79f6b5ff2200de70c36e70a1cfe",
"cert_md5": "78ed7a11b7e03468930c0b7da45a6a41",
"cert_issuer": "CN\u003dAndroid, OU\u003dAndroid, O\u003dGoogle Inc., L\u003dMountain View, ST\u003dCalifornia, C\u003dUS",
"install_time": "2009-01-01 08:00:00",
"update_time": "2009-01-01 08:00:00",
"application_info_flags": 814267973,
"from": "packages"
}
],
"entry": {
"assets/xposed_init": []
}
},
"rule": "AndroidAppFactRule.groovy",
"agendas": [],
"punishes": [],
"server_time": "2025-10-28 09:05:52"
}


{
"fact": {
"type": "StartData",
"build_date": "2025-10-28 09:05:27",
"header": {
"guid": "6c405db8-a909-415d-b72a-398db96e9427",
"platform": "android",
"app_id": "1",
"sdk_version": "1.0.0.220519"
},
"common": {
"user_id": "",
"guid": "6c405db8-a909-415d-b72a-398db96e9427",
"start_id": 1761613527488,
"sdk_version": "1.0.0.220519",
"app_id": 1,
"app_name": "x-genie终端",
"app_package": "com.coolxer.probe.demo",
"app_version": "1.0",
"platform": "android",
"manufacturer": "Google",
"model": "Pixel 3",
"system": "Android",
"system_version": "12",
"net_type": "Wi-Fi",
"lan_ip": "192.168.142.239",
"wan_ip": "0.0.0.0",
"latitude": -1.0,
"longitude": -1.0,
"country": "",
"province": "",
"city": "",
"county": "",
"thoroughfare": "",
"client_time": "2025-10-28 09:05:27"
},
"config": {
"startConfig": [
1,
60.0,
120.0,
10.0,
864000.0
],
"deviceConfig": {
"device": [
"type",
"name",
"systemName",
"systemVersion",
"buildId",
"modelCode",
"countryCode",
"language",
"timeZone",
"currency",
"startElapsedTime",
"wireConnected"
],
"screen": [
"width",
"height",
"brightness",
"density",
"rotation"
],
"cpu": [
"totalCore",
"usedCore",
"type",
"abi"
],
"battery": [
"PROPERTY_STATUS",
"PROPERTY_CHARGE_COUNTER",
"PROPERTY_CURRENT_AVERAGE",
"PROPERTY_CURRENT_NOW",
"PROPERTY_CAPACITY",
"PROPERTY_ENERGY_COUNTER",
"EXTRA_LEVEL",
"EXTRA_SCALE",
"EXTRA_PLUGGED",
"EXTRA_TEMPERATURE",
"EXTRA_PRESENT",
"EXTRA_TECHNOLOGY",
"capacity",
"charging"
],
"uuid": [
"macWlan0",
"macWlan1",
"macP2p0",
"imei",
"imsi",
"iccid",
"serial",
"fileUidData",
"fileUidSystem",
"fileUidCache",
"fileUidVendor",
"androidId",
"widevineId",
"oaid",
"bootId",
"guid"
],
"network": [
"simCount",
"cellType",
"operators",
"country",
"mcc",
"iso",
"mnc",
"allowsVoip",
"lanIp",
"mask",
"broadcast",
"routing",
"signal",
"type"
],
"storage": [
"totalDisk",
"usedDisk",
"totalRam",
"usedRam"
],
"build": [
"TIME",
"FINGERPRINT",
"MODEL",
"MANUFACTURER",
"HARDWARE",
"PRODUCT",
"BOARD",
"BOOTLOADER",
"SERIAL",
"DEVICE",
"HOST",
"TAGS"
],
"opengl": [
"GL_VENDOR",
"GL_VERSION",
"GL_RENDERER",
"GL_SHADING_LANGUAGE_VERSION",
"EGL_VENDOR",
"EGL_VERSION",
"EGL_CLIENT_APIS"
],
"prop": [
"ro.build.user",
"ro.build.version.incremental",
"ro.build.fingerprint",
"ro.build.display.id",
"ro.build.description",
"ro.build.host",
"ro.boot.hardware",
"ro.build.version.incremental",
"ro.build.tags",
"ro.build.type",
"ro.simulated.phone",
"ro.render",
"ro.debuggable",
"ro.secure"
]
},
"fileConfig": {
"exist": [
"/x8/plugins/touch.apk",
"/system/etc/init.dundi.sh",
"ueventd.dundi.rc",
"/data/local/tmp/com.cyjh.ddy.id",
"/storage/emulated/0/.bluestacks.prop",
"fstab.andy",
"/system/bin/windroyed",
"ueventd.andy.rc",
"fstab.nox",
"init.nox.rc",
"ueventd.nox.rc",
"/dev/qemu_pipe",
"/dev/socket/qemud",
"/dev/socket/genyd",
"/dev/socket/baseband_genyd",
"ueventd.android_x86.rc",
"x86.prop",
"ueventd.ttVM_x86.rc",
"init.ttVM_x86.rc",
"fstab.ttVM_x86",
"fstab.vbox86",
"init.vbox86.rc",
"ueventd.vbox86.rc",
"/mnt/apkinstallshare",
"/mnt/apkinstallshareicon",
"/sdcard/ldsdk",
"/sdcard/Android/data/com.android.flysilkworm",
"/system/xbin/ku.sud",
"/data/local/su",
"/data/local/bin/su",
"/data/local/xbin/su",
"/sbin/su",
"/su/bin/su",
"/system/bin/su",
"/system/bin/.ext/su",
"/system/bin/failsafe/su",
"/system/sd/xbin/su",
"/system/usr/we-need-root/su",
"/system/xbin/su",
"/system/sbin/su/su",
"/system/app/Superuser.apk",
"/system/lib/libxposed_art.so",
"/system/lib64/libxposed_art.so",
"/sbin/.magisk/modules/riru_lsposed",
"/system/lib64/libSubstrateRun.so",
"/system/lib64/libsubstrate-dvm.so",
"/system/lib64/libSubstrateJNI.so",
"/system/lib64/libsubstrate.so",
"/system/lib/libsubstrate-dvm.so",
"/system/lib/libsubstrate.so",
"/system/lib/libSubstrateJNI.so",
"/system/lib/libSubstrateRun.so",
"/sbin/magisk",
"/data/adb/magisk",
"/sbin/.magisk",
"/cache/.disable_magisk",
"/dev/.magisk.unblock",
"/cache/magisk.log",
"/data/adb/magisk.img",
"/data/adb/magisk.db",
"/data/adb/.boot_count",
"/data/adb/magisk_simple",
"/init.magisk.rc",
"/data/dalvik-cache/profiles/com.topjohn"
],
"context": [
"/proc/tty/drivers",
"/proc/cpuinfo",
"/proc/self/mounts",
"/proc/version"
],
"fileList": [
"/mnt/apkinstallshare::^LDS_"
],
"md5": [
"/system/etc/security/otacerts.zip"
],
"access": [
"/data/data/\u003cpackage-name\u003e/"
]
},
"runtimeConfig": {
"process": [
".*(ldinit).*",
"^root",
".*frida.*"
],
"service": [
".*"
],
"usageStats": [
".*"
],
"hasClass": [
"me.weishu.exposed.ExposedApplication"
],
"hasInstance": [
"de.robv.android.xposed.XposedHelpers",
"de.robv.android.xposed.XposedBridge"
],
"hasService": [
"user.xposed.system"
],
"mount": [
".*"
],
"cmd": [
"which su"
],
"display": [
".*"
],
"custom": [
"usbDebug",
"tcpDebug",
"mockLocation",
"debugConnect",
"ptrace",
"frida"
]
},
"appConfig": {
"installed": [
".*"
],
"entry": [
"assets/xposed_init"
]
},
"netConfig": {
"port": [
"8811",
"8813"
],
"socket": [
"23946",
"31415",
"27042",
"27043"
],
"uds": [
"re.frida.server"
],
"netInterface": [
".*"
],
"sslPing": [
"https://www.baidu.com"
],
"httpProxy": [
".*"
],
"hosts": [
".*"
],
"certificate": [
".*HttpCanary.*"
],
"custom": [
"network",
"multiSocket"
]
},
"selfAppConfig": {
"base": [
".*"
],
"procMaps": [
".*"
]
},
"eventConfig": {
"memModify": [
".*"
]
},
"locationConfig": {
"base": [
"time",
"speed",
"latitude",
"longitude",
"country",
"province",
"city",
"county",
"thoroughfare"
]
},
"injectConfig": {
"libHook": [
"libmytest.so,libc.so,read",
"libmytest.so,libc.so,open"
],
"javaHook": [
"19,30,android/hardware/Camera,setPreviewCallbackWithBuffer,(Landroid/hardware/Camera$PreviewCallback;)V,false",
"19,30,android/hardware/Camera,setHasPreviewCallback,(ZZ)V,false"
],
"xposedHook": [
"android.telephony.TelephonyManager#getCellLocation",
"android.telephony.TelephonyManager#getNeighboringCellInfo",
"android.net.wifi.WifiManager#getScanResults",
"android.location.LocationManager#getGpsStatus",
"android.location.Location#getLongitude",
"android.location.LocationManager#requestLocationUpdates",
"android.location.LocationManager#requestSingleUpdate"
],
"proxyHook": [
"android.content.pm.IPackageManager$Stub$Proxy"
]
},
"javaConfig": {
"exceptionName": [
".*"
]
},
"nativeConfig": {},
"anrConfig": {},
"activityConfig": {
"className": [
".*"
]
},
"buttonConfig": {
"methodName": [
".*"
]
},
"privacyConfig": {
"provider": [
",com.android.contacts,.*",
",sms"
],
"serviceInterface": [
",com.android.internal.telephony.ISms,isms",
"21-27,com.android.internal.telephony.IMms,imms",
",com.android.internal.telephony.ITelephony,phone",
",com.android.internal.telephony.ITelephonyRegistry,telephony.registry",
",com.android.internal.telephony.IPhoneSubInfo,iphonesubinfo",
",android.location.ILocationManager,location",
"19-30,android.hardware.ICameraService,media.camera",
",android.net.wifi.IWifiManager,wifi",
",android.bluetooth.IBluetoothManager,bluetooth_manager"
],
"serviceField": [
",android.content.pm.IPackageManager,android.app.ActivityThread,sPackageManager"
],
"base": [
"^(?!.*com\\.coolxer|getActivePhoneType$|getActivePhoneTypeForSubscriber$|getActivePhoneTypeForSlot$).*$"
]
},
"urlConfig": {
"url": [
".*"
]
}
}
},
"rule": "AndroidStartFactRule.groovy",
"agendas": [
{
"tag": "启动",
"source": "192.168.142.239",
"level": "NORMAL"
}
],
"punishes": [],
"server_time": "2025-10-28 09:05:41"
}

