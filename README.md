## Serial Manager

[Обсуждение на pccar.ru](http://pccar.ru/showthread.php?t=24120)

<img src="https://cloud.githubusercontent.com/assets/3936845/14065232/ca2985c6-f443-11e5-8cf0-37bf12f44809.png" width="240"> <img src="https://cloud.githubusercontent.com/assets/3936845/14065231/ca2776f0-f443-11e5-94c0-b82fc1c76b84.png" width="240"> <img src="https://cloud.githubusercontent.com/assets/3936845/14435783/51e91190-003b-11e6-9e5f-827bb1ac9264.png" width="240">

## Алгоритм работы
При подключении Arduino, запускается сервис, который считывает информацию
с последовательного порта. При получении информации в формате **_<ключ:значение>_**
будет исполнена заранее настроенная (на данный **_ключ_**) команда. Если
**_ключ_** не был настроен в программе или была включена опция **`Сквозная команда`**,
то создаётся Broadcast Intent, который могут получить сторонние программы.

При блокировке экрана (`ACTION_SCREEN_OFF`) фоновый сервис завершит работу.

При разблокировке экрана (`ACTION_USER_PRESENT`) программа автоматически
подключается к Arduino.

## Интеграция
При отсутствии настроенного **_<ключ:значение>_** или при включенной опции 
**`Сквозная команда`**, Serial Manager создаёт Broadcast Intent со следующими
параметрами:

* Action: `kg.delletenebre.serial.NEW_DATA`
* Extras: `key`, `value`

## Виджеты
Используя встроенные виджеты **`Serial Manager Receive`** Вы сможете, например, выводить данные с подключенных
к Arduino датчиков.

<img src="https://cloud.githubusercontent.com/assets/3936845/14065233/ca29fd76-f443-11e5-9352-43e9c6050e1b.png" width="240"> <img src="https://cloud.githubusercontent.com/assets/3936845/14065234/ca2b695e-f443-11e5-85b1-f1dceb48b5a2.png" width="240">

По умолчанию в виджетах используется шрифт Font Awesome и Вы можете совместно
с текстом использовать иконки. Для вывода иконки необходимо использовать 
следующий формат **`\uXXXX`**, где **XXXX** код Unicode. 
Например для вывода иконки Android:

<img src="https://cloud.githubusercontent.com/assets/3936845/14065225/c9d0f744-f443-11e5-8513-2b4c3b359b12.png" width="240"> <img src="https://cloud.githubusercontent.com/assets/3936845/14065227/ca0ad8b0-f443-11e5-8667-00c10b2c5280.png" width="240"> <img src="https://cloud.githubusercontent.com/assets/3936845/14065228/ca0b2d60-f443-11e5-86b8-9e8c5692c0ea.png" width="240">

Можно использовать любой шрифт, для этого в настройках виджета
необходимо включить опцию **`Использовать свой шрифт`** и указать путь к ttf
файлу шрифта (предварительно скопированного во внутреннюю память устройства).

Для вывода текста по вертикали используется управляющий символ перевода на новую
строку `\n`

Пример: `Текст\nперед\nзначением\n\uf17b\n`

<img src="https://cloud.githubusercontent.com/assets/3936845/14065230/ca0e1e4e-f443-11e5-82cd-27a1007e1334.png" width="240"> <img src="https://cloud.githubusercontent.com/assets/3936845/14065229/ca0b7eb4-f443-11e5-9a56-0499ea4b3e47.png" width="240">

## Виджет отправки данных
В версии 1.4 добавлен виджет **`Serial Manager Send`**, который позволяет отправлять данные на Arduino.

При включении опции `Виджет-переключатель`, Вы сможете последоватьльно отправлять разные данные, используя один и тот же виджет. Как пример - переключатель on/off. В папке arduino находится скетч [sketch_receive_from_android.ino](https://github.com/delletenebre/SerialManager/blob/master/arduino/sketch_receive_from_android.ino), который имеет следующую логику для Arduino: при считывании из последовательного порта `on` подаётся питание на пин 8, при получении `off` убирается питание. Подключив светодиод можно получить наглядную индикацию.

Добавьте новый виджет **`Serial Manager Send`** и настройте следующим образом:
* `Виджет-переключатель`: ☑
* `Отправляемые данные`: on|off 
* `Текст / Иконка`: \uf186|\uf185
* `Цвет шрифта`: #9e9e9e|yellow
* `Размер шрифта`: 64

Подключите Arduino к устройству Android и нажимайте на виджет. При отправленной команде `on` виджет будет иметь жёлтое солнце, при отправленной команде `off` - серый полумесяц.

<img src="https://cloud.githubusercontent.com/assets/3936845/14588703/284bfe3c-04f1-11e6-8cea-b2ff694b7a6e.png" width="240"> <img src="https://cloud.githubusercontent.com/assets/3936845/14588704/284cca9c-04f1-11e6-9c34-3a403b496d93.png" width="240"> <img src="https://cloud.githubusercontent.com/assets/3936845/14588705/28735e0a-04f1-11e6-9f46-20e38107ead8.png" width="240">

Вы можете настроить сколько угодно переключаемых команд, для этого в поле `Отправляемые данные` добаляйте команды разделённые вертикальной чертой (pipe): `|`. Например `pie|eclair|honey|marshmallow`; данные будут отправляться следующим образом:
* нажатие 1: pie
* нажатие 2: eclair
* нажатие 3: honey
* нажатие 4: marshmallow
* нажатие 5: pie
* нажатие 6: eclair
* нажатие 7: honey
* ...

Таким же образом можно настроить поля: `Текст / Иконка`, `Цвет шрифта`, `Размер шрифта`, `Цвет фона`.

## Библиотеки
* [UsbSerial](https://github.com/felHR85/UsbSerial)
* [Font Awesome 4.6.1](http://fortawesome.github.io/Font-Awesome/)
* [Material Dialogs](https://github.com/afollestad/material-dialogs)
* [colorpicker](https://github.com/martin-stone/hsv-alpha-color-picker-android)

## Альтернативы
* [Remote Inputs Manager / Remote steering wheel control](http://forum.xda-developers.com/showthread.php?t=2635159)
