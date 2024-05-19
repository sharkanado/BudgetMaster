import React from 'react';
import {View, Text} from 'react-native';
import {AppTabParamList} from '@/lib/types';
import {BottomTabScreenProps} from '@react-navigation/bottom-tabs';

type MoreProps = BottomTabScreenProps<AppTabParamList, 'More'>;

const More: React.FC<MoreProps> = ({navigation}) => {
  return (
    <View style={{flex: 1, justifyContent: 'center'}}>
      <Text>More</Text>
    </View>
  );
};

export default More;
