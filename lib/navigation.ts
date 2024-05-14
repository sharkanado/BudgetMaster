import {ProfileScreen, HomeScreen} from '@/screens';
import {NativeStackNavigationOptions} from '@react-navigation/native-stack';

type ScreenConfig = {
  name: string;
  component: React.ComponentType<any>;
  options?: NativeStackNavigationOptions;
};

const applicationActivities: ScreenConfig[] = [
  {name: 'Profile', component: ProfileScreen},
  {name: 'Home', component: HomeScreen},
];

export default applicationActivities;
