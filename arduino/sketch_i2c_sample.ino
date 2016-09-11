#include <Wire.h>

String i2cDataForSend = ""; //буфер 

void setup() {
  Serial.begin(115200);
  Wire.begin(3); // подключаемся к шине i2c с адресом #3
  Wire.onReceive(receiveData); // функция при получении данных от Android
  Wire.onRequest(sendData); // функция для отправки данных в Android
}

int counter = 0; //ДЛЯ ТЕСТА - МОЖНО УДАЛИТЬ
void loop() {
  /* Смысл такой - везде пишут, что Android может быть только "мастером"
   * на шине i2c, соотверственно Arduino не может посылать запрос с тем,
   * что у неё есть данные, вернее Android не будет принимать сообщения
   * просто так.
   * На данный момент выход такой: при каком-либо событии записывать
   * <ключ:значение> в буфер (в данном скетче это переменная i2cDataForSend),
   * а Android периодически будет запрашивать данные. 
   * В теории, вполне нормально, что в буфере могут скопиться данные
   * следующего вида "<key1:value1><key2:value2><key3:value2>"
   * Очистка буфера происходит в функции sendData()
    */
  
  if (counter%10 == 0) { //ДЛЯ ТЕСТА - МОЖНО УДАЛИТЬ
    i2cDataForSend += "<test:" + String(counter) + ">"; //ДЛЯ ТЕСТА - МОЖНО УДАЛИТЬ
  } //ДЛЯ ТЕСТА - МОЖНО УДАЛИТЬ
  delay(100); //ДЛЯ ТЕСТА -  МОЖНО УДАЛИТЬ
  counter++; //ДЛЯ ТЕСТА - МОЖНО УДАЛИТЬ
}



void receiveData(int byteCount) {
  String i2cData = "";
  
  while (Wire.available()) {
    char character = Wire.read(); // считываем один символ
    
    if (character) { // если считанные данные являются символом то...
      i2cData.concat(character); // сохраняем в переменную i2cData
    }
  }

  //**** Обработка принятого сообщения ****//
  if (i2cData.length() > 0) {
    // Приняли сообщение от SerialManager - обрабатываем
    Serial.println(i2cData);//В качестве теста смотрим принятые данные на компьютере
  }
}


void sendData() {
  const int len = i2cDataForSend.length() + 1;
  if (len > 1) {
    char buf[len];
    i2cDataForSend.toCharArray(buf, len); 
    Wire.write(buf); // Шлём данные в SerialManager
    i2cDataForSend = "";// Очищаем буфер
  }
}


