import { useEffect } from 'react';
import { Text, View, StyleSheet } from 'react-native';
import {
  multiply,
  setDefaultMessage,
  getAllMessages,
} from 'react-native-messager';

const result = multiply(3, 7);

export default function App() {
  useEffect(() => {
    try {
      setDefaultMessage().then((result) => {
        console.log('result is', result);
      });
    } catch (err) {
      console.log(err);
      getAllMessages(null).then((messages) => {
        console.log('MESSAGES', messages);
      });
    }
  }, []);
  return (
    <View style={styles.container}>
      <Text>Result: {result}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
