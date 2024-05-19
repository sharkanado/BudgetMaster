import React from 'react';
import {View, TextInput, TextInputProps} from 'react-native';

interface TextFieldProps extends TextInputProps {
  placeholder: string;
}

const TextField = (props: TextFieldProps) => {
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
        {...props}
      />
    </View>
  );
};

export default TextField;
