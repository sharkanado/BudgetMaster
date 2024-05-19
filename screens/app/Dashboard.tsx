import React from 'react';
import {View, Text} from 'react-native';
import {AppTabParamList} from '@/lib/types';
import {BottomTabScreenProps} from '@react-navigation/bottom-tabs';

type DashboardProps = BottomTabScreenProps<AppTabParamList, 'Dashboard'>;

const DashboardScreen: React.FC<DashboardProps> = ({navigation}) => {
  return (
    <View style={{flex: 1, justifyContent: 'center'}}>
      <Text>Main Dashboard</Text>
    </View>
  );
};

export default DashboardScreen;
