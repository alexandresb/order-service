# construction de l'image
custom_build(
    # nom de l'image
    ref = 'order-service',
    #commande pour créer l'image OCI avec Buildpacks
    command = './gradlew bootBuildImage --imageName $EXPECTED_REF',
    #fichiers / dossiers observés par Tilt pour déclencher un nouveau build en cas de mise à jour
    deps = ['build.gradle', 'src']
)

# déploiement dans K8s
k8s_yaml(['k8s/order_deployment.yaml', 'k8s/order_service.yaml'])

# port-forwarding
k8s_resource('order-service', port_forwards=['9002'])