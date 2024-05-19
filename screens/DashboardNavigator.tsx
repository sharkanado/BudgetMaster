import React from 'react';
import {createBottomTabNavigator} from '@react-navigation/bottom-tabs';
import {AppTabParamList} from '@/lib/types';
import {Budgets, Dashboard, More} from '@/screens/app';

const Tab = createBottomTabNavigator<AppTabParamList>();

const DashboardNavigator = () => {
  return (
    <Tab.Navigator
      screenOptions={{headerShown: false}}
      initialRouteName="Dashboard"
    >
      <Tab.Screen name="Dashboard" component={Dashboard} />
      <Tab.Screen name="Budgets" component={Budgets} />
      <Tab.Screen name="More" component={More} />
    </Tab.Navigator>
  );
};

export default DashboardNavigator;
