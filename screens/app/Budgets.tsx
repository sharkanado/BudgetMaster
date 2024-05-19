import React from 'react';
import {View, Text} from 'react-native';
import {AppTabParamList} from '@/lib/types';
import {BottomTabScreenProps} from '@react-navigation/bottom-tabs';

type BudgetsProps = BottomTabScreenProps<AppTabParamList, 'Budgets'>;

const Budgets: React.FC<BudgetsProps> = ({navigation}) => {
  return (
    <View style={{flex: 1, justifyContent: 'center'}}>
      <Text>Budgets</Text>
    </View>
  );
};

export default Budgets;
