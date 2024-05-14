import {NativeStackNavigationProp} from '@react-navigation/native-stack';
import {Button} from 'react-native';
import {RootStackParamList} from '../lib/types';

type HomeScreenProps = {
  navigation: NativeStackNavigationProp<RootStackParamList, 'Home'>;
};

const HomeScreen: React.FC<HomeScreenProps> = ({navigation}) => {
  return (
    <Button
      title="Go to Jane's profile"
      onPress={() => navigation.navigate('Profile', {name: 'Jane'})}
    />
  );
};

export default HomeScreen;

// const styles = StyleSheet.create({
//   container: {
//     flex: 1,
//     backgroundColor: '#ff0f',
//     alignItems: 'center',
//     justifyContent: 'center',
//   },
// });
