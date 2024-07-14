import React from 'react';
import {View, Text, Button} from 'react-native';
import {AppTabParamList} from '@/lib/types';
import {BottomTabScreenProps} from '@react-navigation/bottom-tabs';

type DashboardProps = BottomTabScreenProps<AppTabParamList, 'Dashboard'>;

const DashboardScreen: React.FC<DashboardProps> = ({navigation}) => {
  return (
    <View style={{flex: 1, marginTop: 80}}>
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 10,
        }}
      >
        <SummaryView amount="2220" name="Summary" color="pink" size="big" />
        <View style={{gap: 10}}>
          <SummaryView
            amount="220"
            name="Receivable"
            color="green"
            size="small"
          />
          <SummaryView amount="700" name="Debt" color="red" size="small" />
        </View>
      </View>
      <Text>Ostatnie wydatki</Text>
      <View style={{gap: 10}}>
        <View>
          <Text>Wydatek1</Text>
        </View>
      </View>
      <Button onPress={() => console.log('aaaasr')} title="See details" />
    </View>
  );
};

export default DashboardScreen;

const SummaryView = ({
  amount,
  name,
  color,
  size,
}: {
  amount: string;
  name: string;
  color: string;
  size: 'small' | 'big';
}) => {
  return (
    <View
      style={{
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <View
        style={{
          borderRadius: 100,
          width: size === 'small' ? 100 : 150,
          height: size === 'small' ? 100 : 150,
          backgroundColor: color,
          alignItems: 'center',
          justifyContent: 'center',
          overflow: 'hidden',
        }}
      >
        <Text
          style={{
            fontSize: size === 'small' ? 16 : 20,
            fontWeight: 500,
            paddingHorizontal: 10,
          }}
          numberOfLines={1}
        >
          {amount} zł
        </Text>
      </View>
      <Text style={{fontSize: size === 'small' ? 12 : 14}}>{name}</Text>
    </View>
  );
};
