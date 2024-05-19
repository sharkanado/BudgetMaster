import React, {useState} from 'react';
import {View, TextInput} from 'react-native';

const TextField = ({placeholder}: {placeholder: string}) => {
  const [value, setValue] = useState('');

  const handleTextChange = (text: string) => {
    setValue(text);
  };

  return (
    <View>
      <TextInput
        style={{
          height: 40,
          borderWidth: 1,
          padding: 10,
          borderRadius: 4,
          borderColor: '#ddd',
        }}
        onChangeText={handleTextChange}
        value={value}
        placeholder={placeholder}
      />
    </View>
  );
};

export default TextField;
