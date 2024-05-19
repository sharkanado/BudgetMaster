import {
  ProfileScreen,
  HomeScreen,
  SignIn,
  SignUp,
  DashboardNavigator,
} from '@/screens';
import {Budgets, Dashboard, More} from '@/screens/app';

import {NativeStackNavigationOptions} from '@react-navigation/native-stack';

type ScreenConfig = {
  name: string;
  component: React.ComponentType<any>;
  options?: NativeStackNavigationOptions;
};

const applicationActivities: ScreenConfig[] = [
  {name: 'Profile', component: ProfileScreen},
  {name: 'Home', component: HomeScreen},
  {name: 'SignIn', component: SignIn},
  {name: 'SignUp', component: SignUp},
  {name: 'DashboardNavigator', component: DashboardNavigator},
  {name: 'Budgets', component: Budgets},
  {name: 'MainDashboard', component: Dashboard},
  {name: 'More', component: More},
];

export default applicationActivities;
