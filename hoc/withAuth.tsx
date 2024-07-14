// import React, {useState, useEffect} from 'react';
// import {getAuth, onAuthStateChanged} from 'firebase/auth';

// interface WithAuthProps extends RouterProps {
//   isAuthenticated: boolean;
// }

// const withAuth = (
//   WrappedComponent: React.ComponentType<any & WithAuthProps>
// ): React.FC<any> => {
//   const WithAuth: React.FC<any & WithAuthProps> = (props) => {
//     const [isAuthenticated, setIsAuthenticated] = useState(false);
//     const dispatch = useAppDispatch();
//     const navigate = useNavigate();

//     useEffect(() => {
//       if (userData?.data) checkAuth();
//     }, [userData]);

//     const checkAuth = async () => {
//       const auth = getAuth();
//       onAuthStateChanged(auth, (user) => {
//         if (user) {
//           const uid = user.uid;
//           // ...
//         } else {
//           // User is signed out
//           // ...
//         }
//       });
//     };

//     if (isAuthenticated) {
//       return <WrappedComponent {...props} isAuthenticated={isAuthenticated} />;
//     } else {
//       return null; // Or a loading spinner or some other UI element
//     }
//   };

//   return WithAuth;
// };

// export default withAuth;
