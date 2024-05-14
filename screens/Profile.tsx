import {NativeStackNavigationProp} from '@react-navigation/native-stack';
import {RouteProp} from '@react-navigation/native';
import {Text} from 'react-native';
import {RootStackParamList} from '../lib/types';

type ProfileScreenProps = {
  navigation: NativeStackNavigationProp<RootStackParamList, 'Profile'>;
  route: RouteProp<RootStackParamList, 'Profile'>;
};

const ProfileScreen: React.FC<ProfileScreenProps> = ({route}) => {
  return <Text>This is {route.params.name}'s profile</Text>;
};
export default ProfileScreen;
