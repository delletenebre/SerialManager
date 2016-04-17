#define DEBUG

const int LED_PIN = 8;
String serialData = "";
boolean isSerialDataReceived = false;

void setup() {
  Serial.begin(9600);
  pinMode(LED_PIN, OUTPUT);
}


void loop() {
  while (Serial.available()) {
    char character = Serial.read();
    if (character == '\n' || character == '\r') {
      isSerialDataReceived = true;
    } else {
      serialData.concat(character);
    }
  }

  if (isSerialDataReceived) {
    if (serialData.length() > 0) {
      #ifdef DEBUG
        Serial.print("Received: ");
        Serial.println(serialData);
      #endif

      //**** Ваша логика ****//
      if (serialData == "on") {
        digitalWrite(LED_PIN, HIGH);
      }
      if (serialData == "off") {
        digitalWrite(LED_PIN, LOW);
      }
      //**** Достаточно логики :) ****//
      
    }
    
    isSerialDataReceived = false;
    serialData = "";
  }
  

  
}
